package ghopt.core.io;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class StarPowerOptimizer {

    public static class OptimalPath {
        public List<Integer> activationTimes;
        public long totalScore;

        public OptimalPath(List<Integer> activationTimes, long totalScore) {
            this.activationTimes = new ArrayList<>(activationTimes);
            this.totalScore = totalScore;
        }

        @Override
        public String toString() {
            return "OptimalPath{" +
                    "activationTimes=" + activationTimes +
                    ", totalScore=" + totalScore +
                    '}';
        }
    }

    private static class State {
        int groupIndex;
        int starPowerMeter; // 0-200 (representing 0-100%)
        boolean starPowerActive;

        State(int groupIndex, int starPowerMeter, boolean starPowerActive) {
            this.groupIndex = groupIndex;
            this.starPowerMeter = starPowerMeter;
            this.starPowerActive = starPowerActive;
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupIndex, starPowerMeter, starPowerActive);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof State)) return false;
            State other = (State) obj;
            return groupIndex == other.groupIndex &&
                    starPowerMeter == other.starPowerMeter &&
                    starPowerActive == other.starPowerActive;
        }
    }

    private static class DPResult {
        long score;
        List<Integer> activations;

        DPResult(long score, List<Integer> activations) {
            this.score = score;
            this.activations = new ArrayList<>(activations);
        }
    }

    private static class GroupInfo {
        int time;
        int noteCount;
        long sustainPoints;
        int baseMultiplier;
        int ticksPerBar;
        int deltaTicks;
        boolean phraseComplete;
    }

    private static final int NOTE_POINTS = 50;
    private static final int SUSTAIN_POINTS_PER_BEAT = 25;
    private static final int MAX_METER = 200;
    private static final int PHRASE_GAIN = 50; // 25% in a 0-200 meter
    private static final int ACTIVATION_THRESHOLD = 100; // 50% in a 0-200 meter
    private static final int DRAIN_PER_BAR = 25; // 12.5% in a 0-200 meter

    public static OptimalPath findOptimalPath(ChartParser.ChartData chartData) {
        return findOptimalPath(chartData, false);
    }

    public static OptimalPath findOptimalPath(ChartParser.ChartData chartData, boolean debug) {
        if (chartData.notes.isEmpty()) {
            return new OptimalPath(new ArrayList<>(), 0);
        }

        List<ChartParser.Note> notes = new ArrayList<>(chartData.notes);
        notes.sort(Comparator.comparingInt(n -> n.time));

        List<GroupInfo> groups = buildGroups(notes, chartData);
        Set<Integer> phraseCompletionTimes = getPhraseCompletionTimes(groups, chartData.starPowerPhrases);
        for (GroupInfo group : groups) {
            group.phraseComplete = phraseCompletionTimes.contains(group.time);
        }

        Map<State, DPResult> memo = new HashMap<>();
        State initial = new State(0, 0, false);
        DPResult result = dpSolve(groups, initial, memo);

        if (debug) {
            printDebugTrace(groups, result.activations);
        }

        return new OptimalPath(result.activations, result.score);
    }

    public static long calculateBaseScore(ChartParser.ChartData chartData) {
        if (chartData.notes.isEmpty()) {
            return 0;
        }

        List<ChartParser.Note> notes = new ArrayList<>(chartData.notes);
        notes.sort(Comparator.comparingInt(n -> n.time));

        List<GroupInfo> groups = buildGroups(notes, chartData);
        
        long totalScore = 0;
        for (GroupInfo group : groups) {
            long groupBasePoints = (long) NOTE_POINTS * group.noteCount + group.sustainPoints;
            totalScore += groupBasePoints * group.baseMultiplier;
        }
        
        return totalScore;
    }

    private static void printDebugTrace(List<GroupInfo> groups, List<Integer> activations) {
        Set<Integer> activationSet = new HashSet<>(activations);
        int meter = 0;
        boolean active = false;
        long score = 0;
        
        System.out.println("\n=== Debug Trace ===");
        int activationCount = 0;
        for (int i = 0; i < groups.size(); i++) {
            GroupInfo g = groups.get(i);
            
            // Gain phrase
            if (g.phraseComplete) {
                meter = Math.min(MAX_METER, meter + PHRASE_GAIN);
                System.out.println("PHRASE at time " + g.time + " | Meter now: " + meter + " (" + (meter/2.0) + "%)");
            }
            
            // Check for activation
            if (activationSet.contains(g.time)) {
                activationCount++;
                active = true;
                System.out.println(">>> ACTIVATE #" + activationCount + " at time " + g.time + " | Meter: " + meter + " (" + (meter/2.0) + "%) | Combo: " + g.baseMultiplier + "x -> " + (g.baseMultiplier * 2) + "x");
            }
            
            // Score this group
            long groupBase = (long) NOTE_POINTS * g.noteCount + g.sustainPoints;
            int mult = g.baseMultiplier * (active ? 2 : 1);
            long groupScore = groupBase * mult;
            score += groupScore;
            
            // Drain
            if (active) {
                int oldMeter = meter;
                int drainUnits = (g.ticksPerBar > 0 && g.deltaTicks > 0) 
                    ? (DRAIN_PER_BAR * g.deltaTicks) / g.ticksPerBar 
                    : 0;
                meter = Math.max(0, meter - drainUnits);
                
                if (meter == 0 && oldMeter > 0) {
                    active = false;
                    System.out.println("<<< SP ENDED after group " + i + " at time " + g.time + " | Drained from " + oldMeter + " to 0");
                }
            }
        }
        System.out.println("Final Score: " + score);
        System.out.println("===================\n");
    }

    private static DPResult dpSolve(List<GroupInfo> groups, State state, Map<State, DPResult> memo) {
        if (state.groupIndex >= groups.size()) {
            return new DPResult(0, new ArrayList<>());
        }

        DPResult cached = memo.get(state);
        if (cached != null) {
            return cached;
        }

        GroupInfo group = groups.get(state.groupIndex);

        int meterAfterGain = state.starPowerMeter + (group.phraseComplete ? PHRASE_GAIN : 0);
        meterAfterGain = Math.min(MAX_METER, meterAfterGain);

        long groupBasePoints = (long) NOTE_POINTS * group.noteCount + group.sustainPoints;
        int baseMultiplier = group.baseMultiplier;

        long bestScore = Long.MIN_VALUE;
        List<Integer> bestPath = new ArrayList<>();

        // Option 1: do not activate now.
        boolean activeNow = state.starPowerActive;
        int effectiveMultiplier = baseMultiplier * (activeNow ? 2 : 1);
        long scoreNoActivate = groupBasePoints * effectiveMultiplier;

        int meterAfterNoActivate = meterAfterGain;
        int meterAfterDrain = applyDrain(meterAfterNoActivate, activeNow, group);
        boolean activeNext = activeNow && meterAfterDrain > 0;

        State nextNoActivate = new State(state.groupIndex + 1, meterAfterDrain, activeNext);
        DPResult resultNoActivate = dpSolve(groups, nextNoActivate, memo);
        long totalNoActivate = scoreNoActivate + resultNoActivate.score;

        if (totalNoActivate > bestScore) {
            bestScore = totalNoActivate;
            bestPath = new ArrayList<>(resultNoActivate.activations);
        }

        // Option 2: activate now if allowed (check after phrase gain).
        if (!state.starPowerActive && meterAfterGain >= ACTIVATION_THRESHOLD) {
            boolean activeNowActivate = true;
            int effectiveMultiplierActivate = baseMultiplier * 2;
            long scoreActivate = groupBasePoints * effectiveMultiplierActivate;

            int meterAfterActivate = meterAfterGain;
            int meterAfterDrainActivate = applyDrain(meterAfterActivate, activeNowActivate, group);
            boolean activeNextActivate = meterAfterDrainActivate > 0;

            State nextActivate = new State(state.groupIndex + 1, meterAfterDrainActivate, activeNextActivate);
            DPResult resultActivate = dpSolve(groups, nextActivate, memo);
            long totalActivate = scoreActivate + resultActivate.score;

            if (totalActivate > bestScore) {
                bestScore = totalActivate;
                bestPath = new ArrayList<>(resultActivate.activations);
                bestPath.add(group.time);
            }
        }

        DPResult best = new DPResult(bestScore, bestPath);
        memo.put(state, best);
        return best;
    }

    private static int applyDrain(int meter, boolean active, GroupInfo group) {
        if (!active || meter <= 0) {
            return meter;
        }
        if (group.ticksPerBar <= 0 || group.deltaTicks <= 0) {
            return meter;
        }

        int drainUnits = (DRAIN_PER_BAR * group.deltaTicks) / group.ticksPerBar;
        int drained = meter - drainUnits;
        return Math.max(0, drained);
    }

    private static List<GroupInfo> buildGroups(List<ChartParser.Note> notes, ChartParser.ChartData chartData) {
        List<GroupInfo> groups = new ArrayList<>();
        if (notes.isEmpty()) {
            return groups;
        }

        int timeSignatureIndex = 0;
        List<ChartParser.TimeSignatureEvent> timeSignatures = chartData.timeSignatures;

        int comboCount = 0;
        int i = 0;
        while (i < notes.size()) {
            int time = notes.get(i).time;
            List<ChartParser.Note> groupNotes = new ArrayList<>();
            while (i < notes.size() && notes.get(i).time == time) {
                groupNotes.add(notes.get(i));
                i++;
            }

            while (timeSignatureIndex + 1 < timeSignatures.size() &&
                    timeSignatures.get(timeSignatureIndex + 1).time <= time) {
                timeSignatureIndex++;
            }

            ChartParser.TimeSignatureEvent ts = timeSignatures.get(timeSignatureIndex);
            int ticksPerQuarterNote = chartData.resolution;
            int ticksPerBar = calculateTicksPerBar(chartData.resolution, ts.numerator, ts.denominator);

            long sustainPoints = 0;
            for (ChartParser.Note note : groupNotes) {
                if (note.duration > 0 && ticksPerQuarterNote > 0) {
                    double quarterNotes = (double) note.duration / ticksPerQuarterNote;
                    double sustainPointsDouble = quarterNotes * SUSTAIN_POINTS_PER_BEAT;
                    sustainPoints += (long) Math.ceil(sustainPointsDouble);
                }
            }

            int baseMultiplier = 1 + (comboCount / 10);
            if (baseMultiplier > 4) baseMultiplier = 4;
            comboCount++;

            GroupInfo info = new GroupInfo();
            info.time = time;
            info.noteCount = groupNotes.size();
            info.sustainPoints = sustainPoints;
            info.baseMultiplier = baseMultiplier;
            info.ticksPerBar = ticksPerBar;
            info.deltaTicks = 0;
            info.phraseComplete = false;
            groups.add(info);
        }

        for (int idx = 0; idx < groups.size() - 1; idx++) {
            GroupInfo current = groups.get(idx);
            GroupInfo next = groups.get(idx + 1);
            current.deltaTicks = Math.max(0, next.time - current.time);
        }

        return groups;
    }

    private static int calculateTicksPerBar(int resolution, int numerator, int denominator) {
        if (denominator <= 0) {
            return resolution * 4; // Default to 4/4 bar
        }
        // A bar contains 'numerator' beats of type 'denominator'
        // Example: 4/4 = 4 quarter notes = 4 * resolution
        // Example: 6/8 = 6 eighth notes = 6 * (resolution/2) = 3 * resolution
        return (resolution * 4 * numerator) / denominator;
    }

    private static Set<Integer> getPhraseCompletionTimes(List<GroupInfo> groups,
                                                         List<ChartParser.StarPowerPhrase> phrases) {
        Set<Integer> completionTimes = new HashSet<>();
        if (groups.isEmpty() || phrases.isEmpty()) {
            return completionTimes;
        }

        List<ChartParser.StarPowerPhrase> sortedPhrases = new ArrayList<>(phrases);
        sortedPhrases.sort(Comparator.comparingInt(p -> p.start));

        int groupIdx = 0;
        for (ChartParser.StarPowerPhrase phrase : sortedPhrases) {
            while (groupIdx < groups.size() && groups.get(groupIdx).time < phrase.start) {
                groupIdx++;
            }

            Integer lastTime = null;
            int scanIdx = groupIdx;
            while (scanIdx < groups.size() && groups.get(scanIdx).time <= phrase.end) {
                lastTime = groups.get(scanIdx).time;
                scanIdx++;
            }

            groupIdx = scanIdx;
            if (lastTime != null) {
                completionTimes.add(lastTime);
            }
        }

        return completionTimes;
    }

    public static void main(String[] args) throws java.io.IOException {
        if (args.length < 1) {
            System.out.println("Usage: java ghopt.core.io.StarPowerOptimizer <chart-file> [output-image-path]");
            return;
        }

        String chartPath = args[0];
        String outputPath = args.length > 1 ? args[1] : "output/chart_with_activations.png";

        ChartParser.ChartData chartData = ChartParser.parseChart(chartPath);
        long baseScore = calculateBaseScore(chartData);
        OptimalPath optimalPath = findOptimalPath(chartData, true);

        System.out.println("=== Star Power Optimizer Results ===");
        System.out.println("Base Score (no star power): " + baseScore);
        System.out.println("Optimal Score: " + optimalPath.totalScore);
        System.out.println("Score Improvement: +" + (optimalPath.totalScore - baseScore) + " (" + 
                           String.format("%.1f", ((optimalPath.totalScore - baseScore) * 100.0 / baseScore)) + "%)" );
        System.out.println("Activation Times: " + optimalPath.activationTimes);
        System.out.println();

        ChartParser.generateChartImage(chartData, outputPath, optimalPath.activationTimes);
        System.out.println("Chart image generated at: " + outputPath);
    }
}
