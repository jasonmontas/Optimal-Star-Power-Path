import java.util.*;

public class StarPowerOptimizer {

    public static class OptimalPath {
        public List<Integer> activationTimes; // times to activate star power
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
        int noteIndex;
        int starPowerMeter; // 0-200 (representing 0-100%)
        boolean starPowerActive;
        int activeDuration; // ticks remaining in current activation

        State(int noteIndex, int starPowerMeter, boolean starPowerActive, int activeDuration) {
            this.noteIndex = noteIndex;
            this.starPowerMeter = starPowerMeter;
            this.starPowerActive = starPowerActive;
            this.activeDuration = activeDuration;
        }

        @Override
        public int hashCode() {
            return Objects.hash(noteIndex, starPowerMeter, starPowerActive, activeDuration);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof State)) return false;
            State other = (State) obj;
            return noteIndex == other.noteIndex &&
                    starPowerMeter == other.starPowerMeter &&
                    starPowerActive == other.starPowerActive &&
                    activeDuration == other.activeDuration;
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

    /**
     * Calculate base score (no star power activation).
     */
    public static long calculateBaseScore(ChartParser.ChartData chartData) {
        return chartData.notes.size() * 10L; // 10 points per note baseline
    }

    /**
     * Calculate score with star power activations applied.
     * 
     * @param chartData The parsed chart data containing notes and star power phrases
     * @return OptimalPath containing activation times and total score
     */
    public static OptimalPath findOptimalPath(ChartParser.ChartData chartData) {
        if (chartData.notes.isEmpty()) {
            return new OptimalPath(new ArrayList<>(), 0);
        }

        // DP memoization: state -> (score, activation times)
        Map<State, DPResult> memo = new HashMap<>();

        // Sort notes by time (should already be sorted, but ensure it)
        List<ChartParser.Note> notes = new ArrayList<>(chartData.notes);
        notes.sort(Comparator.comparingInt(n -> n.time));

        // Create set of star power phrase times for O(1) lookup
        Set<Integer> starPowerPhraseStarts = new HashSet<>();
        for (ChartParser.StarPowerPhrase phrase : chartData.starPowerPhrases) {
            starPowerPhraseStarts.add(phrase.start);
        }

        // Start DP from state 0
        State initialState = new State(0, 0, false, 0);
        DPResult result = dpSolve(notes, chartData.starPowerPhrases, initialState, memo, 
                                   starPowerPhraseStarts);

        return new OptimalPath(result.activations, result.score);
    }

    /**
     * Recursive DP solver with memoization.
     */
    private static DPResult dpSolve(List<ChartParser.Note> notes,
                                     List<ChartParser.StarPowerPhrase> phrases,
                                     State state,
                                     Map<State, DPResult> memo,
                                     Set<Integer> phraseStarts) {

        // Base case: processed all notes
        if (state.noteIndex >= notes.size()) {
            return new DPResult(0, new ArrayList<>());
        }

        // Check memo
        if (memo.containsKey(state)) {
            return memo.get(state);
        }

        ChartParser.Note currentNote = notes.get(state.noteIndex);
        
        // Calculate base score for current note (1x or 2x if star power active)
        int multiplier = state.starPowerActive ? 2 : 1;
        long noteScore = 10 * multiplier; // 10 points per note baseline

        // Update star power meter if we hit a phrase
        int newMeter = state.starPowerMeter;
        for (ChartParser.StarPowerPhrase phrase : phrases) {
            if (phrase.start == currentNote.time) {
                newMeter = Math.min(200, newMeter + 25);
                break;
            }
        }

        // Calculate new activation duration (decrements as we process notes)
        int newDuration = state.activeDuration - (currentNote.time - 
                (state.noteIndex > 0 ? notes.get(state.noteIndex - 1).time : currentNote.time));
        newDuration = Math.max(0, newDuration);

        // Option 1: Continue with current state (no activation or deactivation)
        State nextState = new State(state.noteIndex + 1, newMeter, 
                                    state.starPowerActive && newDuration > 0, newDuration);
        DPResult option1 = dpSolve(notes, phrases, nextState, memo, phraseStarts);
        long score1 = noteScore + option1.score;
        List<Integer> path1 = new ArrayList<>(option1.activations);

        DPResult best = new DPResult(score1, path1);

        // Option 2: Activate star power if not active and meter >= 100
        if (!state.starPowerActive && newMeter >= 50) {
            int spDuration = 8000; // Arbitrary default duration for activation
            State activateState = new State(state.noteIndex + 1, newMeter - 50, 
                                           true, spDuration);
            DPResult option2 = dpSolve(notes, phrases, activateState, memo, phraseStarts);
            long score2 = noteScore + option2.score;
            List<Integer> path2 = new ArrayList<>(option2.activations);
            path2.add(currentNote.time); // Record activation time

            if (score2 > best.score) {
                best = new DPResult(score2, path2);
            }
        }

        memo.put(state, best);
        return best;
    }

    /**
     * Alternative simpler approach: greedy DP based on note density.
     * Finds sections with highest note density and activates star power there.
     * 
     * @param chartData The parsed chart data
     * @param windowSize Time window to analyze note density (in ticks)
     * @return OptimalPath with activations placed at highest-density sections
     */
    public static OptimalPath findOptimalPathGreedy(ChartParser.ChartData chartData, 
                                                     int windowSize) {
        List<ChartParser.Note> notes = new ArrayList<>(chartData.notes);
        if (notes.isEmpty()) {
            return new OptimalPath(new ArrayList<>(), 0);
        }

        notes.sort(Comparator.comparingInt(n -> n.time));
        int maxTime = notes.get(notes.size() - 1).time;

        // Calculate note density for each time window
        List<Integer> densities = new ArrayList<>();
        List<Integer> windowStarts = new ArrayList<>();

        for (int windowStart = 0; windowStart <= maxTime; windowStart += windowSize) {
            int windowEnd = windowStart + windowSize;
            int noteCount = 0;

            for (ChartParser.Note note : notes) {
                if (note.time >= windowStart && note.time < windowEnd) {
                    noteCount++;
                }
            }

            densities.add(noteCount);
            windowStarts.add(windowStart);
        }

        // Find top 3 densest sections (can have multiple star power activations)
        List<Integer> activationTimes = new ArrayList<>();
        long totalScore = 0;

        for (int i = 0; i < Math.min(3, densities.size()); i++) {
            int maxIdx = 0;
            for (int j = 1; j < densities.size(); j++) {
                if (densities.get(j) > densities.get(maxIdx)) {
                    maxIdx = j;
                }
            }

            if (densities.get(maxIdx) > 0) {
                activationTimes.add(windowStarts.get(maxIdx));
                totalScore += densities.get(maxIdx) * 20; // Score bonus for density
            }
            densities.set(maxIdx, 0); // Mark as used
        }

        return new OptimalPath(activationTimes, totalScore);
    }

    public static void main(String[] args) throws java.io.IOException {
        if (args.length < 1) {
            System.out.println("Usage: java StarPowerOptimizer <chart-file> [output-image-path]");
            return;
        }

        String chartPath = args[0];
        String outputPath = args.length > 1 ? args[1] : "output/chart_with_activations.png";

        ChartParser.ChartData chartData = ChartParser.parseChart(chartPath);
        OptimalPath optimalPath = findOptimalPath(chartData);
        long baseScore = calculateBaseScore(chartData);

        System.out.println("=== Star Power Optimizer Results ===");
        System.out.println("Base Score (no star power): " + baseScore);
        System.out.println("Optimal Score: " + optimalPath.totalScore);
        System.out.println("Activation Times: " + optimalPath.activationTimes);
        System.out.println();

        // Generate visualization
        ChartParser.generateChartImage(chartData, outputPath, optimalPath.activationTimes);
        System.out.println("Chart image generated at: " + outputPath);
    }
}
