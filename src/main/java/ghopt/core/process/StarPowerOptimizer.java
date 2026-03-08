package ghopt.core.process;

import ghopt.core.model.ChartData;
import ghopt.core.model.Note;
import ghopt.core.model.StarPowerPhrase;

import java.util.*;

public class StarPowerOptimizer {

    public static class OptimalPath {
        public List<Integer> activationTimes;
        public long totalScore;

        public OptimalPath(List<Integer> activationTimes, long totalScore) {
            this.activationTimes = new ArrayList<>(activationTimes);
            this.totalScore = totalScore;
        }
    }

    private static class State {
        int groupIndex;
        int starPowerMeter;
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
    private static final int PHRASE_GAIN = 50;
    private static final int ACTIVATION_THRESHOLD = 100;
    private static final int DRAIN_PER_BAR = 25;

    public static OptimalPath findOptimalPath(ChartData chartData) {

        System.out.println("Optimizer running...");
        System.out.println("Notes: " + chartData.getNotes().size());
        System.out.println("Star Power phrases: " + chartData.getStarPowerPhrases().size());

        if (chartData.getNotes().isEmpty()) {
            return new OptimalPath(new ArrayList<>(), 0);
        }

        List<Note> notes = new ArrayList<>(chartData.getNotes());
        notes.sort(Comparator.comparingInt(Note::getTime));

        List<GroupInfo> groups = buildGroups(notes, chartData);

        Set<Integer> phraseCompletionTimes =
                getPhraseCompletionTimes(groups, chartData.getStarPowerPhrases());

        for (GroupInfo group : groups) {
            group.phraseComplete = phraseCompletionTimes.contains(group.time);
        }

        Map<State, DPResult> memo = new HashMap<>();
        State initial = new State(0, 0, false);

        DPResult result = dpSolve(groups, initial, memo);
        System.out.println("Optimizer finished.");
System.out.println("Score: " + result.score);
System.out.println("Activations: " + result.activations);
        return new OptimalPath(result.activations, result.score);
    }

    private static DPResult dpSolve(List<GroupInfo> groups,
                                    State state,
                                    Map<State, DPResult> memo) {

        if (state.groupIndex >= groups.size()) {
            return new DPResult(0, new ArrayList<>());
        }

        DPResult cached = memo.get(state);
        if (cached != null) {
            return cached;
        }

        GroupInfo group = groups.get(state.groupIndex);

        int meterAfterGain = state.starPowerMeter +
                (group.phraseComplete ? PHRASE_GAIN : 0);

        meterAfterGain = Math.min(MAX_METER, meterAfterGain);

        long groupBasePoints =
                (long) NOTE_POINTS * group.noteCount + group.sustainPoints;

        int baseMultiplier = group.baseMultiplier;

        long bestScore = Long.MIN_VALUE;
        List<Integer> bestPath = new ArrayList<>();

        // Option 1: do not activate
        boolean activeNow = state.starPowerActive;

        int effectiveMultiplier = baseMultiplier * (activeNow ? 2 : 1);

        long scoreNoActivate = groupBasePoints * effectiveMultiplier;

        int meterAfterNoActivate = meterAfterGain;

        int meterAfterDrain = applyDrain(
                meterAfterNoActivate,
                activeNow,
                group
        );

        boolean activeNext = activeNow && meterAfterDrain > 0;

        State nextNoActivate =
                new State(state.groupIndex + 1, meterAfterDrain, activeNext);

        DPResult resultNoActivate =
                dpSolve(groups, nextNoActivate, memo);

        long totalNoActivate =
                scoreNoActivate + resultNoActivate.score;

        if (totalNoActivate > bestScore) {
            bestScore = totalNoActivate;
            bestPath = new ArrayList<>(resultNoActivate.activations);
        }

        // Option 2: activate
        if (!state.starPowerActive &&
                meterAfterGain >= ACTIVATION_THRESHOLD) {

            boolean activeNowActivate = true;

            int effectiveMultiplierActivate =
                    baseMultiplier * 2;

            long scoreActivate =
                    groupBasePoints * effectiveMultiplierActivate;

            int meterAfterActivate = meterAfterGain;

            int meterAfterDrainActivate =
                    applyDrain(meterAfterActivate,
                            activeNowActivate,
                            group);

            boolean activeNextActivate =
                    meterAfterDrainActivate > 0;

            State nextActivate =
                    new State(state.groupIndex + 1,
                            meterAfterDrainActivate,
                            activeNextActivate);

            DPResult resultActivate =
                    dpSolve(groups, nextActivate, memo);

            long totalActivate =
                    scoreActivate + resultActivate.score;

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

    private static int applyDrain(int meter,
                                  boolean active,
                                  GroupInfo group) {

        if (!active || meter <= 0) {
            return meter;
        }

        if (group.ticksPerBar <= 0 || group.deltaTicks <= 0) {
            return meter;
        }

        int drainUnits =
                (DRAIN_PER_BAR * group.deltaTicks) / group.ticksPerBar;

        int drained = meter - drainUnits;

        return Math.max(0, drained);
    }

    private static List<GroupInfo> buildGroups(List<Note> notes,
                                               ChartData chartData) {

        List<GroupInfo> groups = new ArrayList<>();

        int comboCount = 0;
        int i = 0;

        while (i < notes.size()) {

            int time = notes.get(i).getTime();

            List<Note> groupNotes = new ArrayList<>();

            while (i < notes.size() &&
                    notes.get(i).getTime() == time) {

                groupNotes.add(notes.get(i));
                i++;
            }

            int resolution = chartData.getResolution();

            int ticksPerBar = resolution * 4; // assume 4/4

            long sustainPoints = 0;

            for (Note note : groupNotes) {

                if (note.getDuration() > 0 && resolution > 0) {

                    double quarterNotes =
                            (double) note.getDuration() / resolution;

                    double sustainPointsDouble =
                            quarterNotes * SUSTAIN_POINTS_PER_BEAT;

                    sustainPoints +=
                            (long) Math.ceil(sustainPointsDouble);
                }
            }

            int baseMultiplier = 1 + (comboCount / 10);

            if (baseMultiplier > 4)
                baseMultiplier = 4;

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

            current.deltaTicks =
                    Math.max(0, next.time - current.time);
        }

        return groups;
    }

    private static Set<Integer> getPhraseCompletionTimes(
            List<GroupInfo> groups,
            List<StarPowerPhrase> phrases) {

        Set<Integer> completionTimes = new HashSet<>();

        if (groups.isEmpty() || phrases.isEmpty()) {
            return completionTimes;
        }

        for (StarPowerPhrase phrase : phrases) {

            Integer lastTime = null;

            for (GroupInfo group : groups) {

                if (group.time >= phrase.getStartTick() &&
                        group.time <= phrase.getEndTick()) {

                    lastTime = group.time;
                }
            }

            if (lastTime != null) {
                completionTimes.add(lastTime);
            }
        }

        return completionTimes;
    }
}