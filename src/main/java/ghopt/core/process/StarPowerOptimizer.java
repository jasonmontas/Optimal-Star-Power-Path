package ghopt.core.process;

import ghopt.core.model.ChartData;
import ghopt.core.model.Note;
import ghopt.core.model.StarPowerPhrase;

import java.util.*;

/**
 * Finds the optimal Star Power activation times to maximise score.
 *
 * Clone Hero scoring rules implemented here:
 *  - Each note hit:          50 pts × combo multiplier
 *  - Chord (n notes):        50 × n pts × combo multiplier
 *  - Sustain:                25 pts per beat × combo multiplier (NOT multiplied per chord note)
 *  - Combo multiplier:       1x→2x→3x→4x every 10 *notes* (not groups/chords)
 *  - SP doubles multiplier:  2x→4x→6x→8x (caps at 8x)
 *  - SP meter:               0–200 units; each completed phrase = +50 (= 1/4 bar)
 *  - Activation threshold:   ≥100 units (= 1/2 bar)
 *  - SP drain:               full bar (200) lasts 8 measures → drain = 25 units/measure
 *    (wiki: "a 1/2 filled bar lasts 4 measures" → full bar = 8 measures)
 *
 * NOTE: Whammy on sustains inside an SP phrase gradually increases the meter.
 * This is not modelled here because whammy input is unknown at optimisation time.
 * The optimizer therefore produces a lower-bound estimate during SP phrases that
 * contain sustains — actual meter may be higher if the player whammies.
 */
public class StarPowerOptimizer {

    // ── Scoring constants ────────────────────────────────────────────────────
    private static final int NOTE_POINTS            = 50;
    private static final int SUSTAIN_POINTS_PER_BEAT = 25;

    // ── SP meter constants ───────────────────────────────────────────────────
    /** Maximum SP meter value (represents a full bar). */
    private static final int MAX_METER          = 200;
    /** Meter gained per completed SP phrase (= 1/4 bar). */
    private static final int PHRASE_GAIN        = 50;
    /** Minimum meter required to activate SP (= 1/2 bar). */
    private static final int ACTIVATION_THRESHOLD = 100;
    /**
     * Meter drained per 4/4 measure while SP is active.
     * Full bar (200) lasts 8 measures → 200/8 = 25 per measure.
     */
    private static final int DRAIN_PER_BAR     = 25;

    // ─────────────────────────────────────────────────────────────────────────

    public static class OptimalPath {
        public List<Integer> activationTimes;
        public long totalScore;

        public OptimalPath(List<Integer> activationTimes, long totalScore) {
            this.activationTimes = new ArrayList<>(activationTimes);
            this.totalScore = totalScore;
        }
    }

    // ── DP state ─────────────────────────────────────────────────────────────

    private static class State {
        final int groupIndex;
        final int starPowerMeter;
        final boolean starPowerActive;

        State(int groupIndex, int starPowerMeter, boolean starPowerActive) {
            this.groupIndex     = groupIndex;
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
            State o = (State) obj;
            return groupIndex     == o.groupIndex &&
                   starPowerMeter == o.starPowerMeter &&
                   starPowerActive == o.starPowerActive;
        }
    }

    private static class DPResult {
        final long score;
        final List<Integer> activations;

        DPResult(long score, List<Integer> activations) {
            this.score       = score;
            this.activations = new ArrayList<>(activations);
        }
    }

    // ── Group (one timestamp, potentially a chord) ────────────────────────────

    private static class GroupInfo {
        int  time;
        int  noteCount;       // number of individual notes in the chord
        long sustainPoints;   // sustain pts for this group (NOT multiplied per chord note)
        int  baseMultiplier;  // combo multiplier BEFORE SP doubling (1–4)
        int  ticksPerBar;     // ticks in one 4/4 measure
        int  deltaTicks;      // ticks until the next group (for drain calculation)
        boolean phraseComplete; // true if this group is the last note of an SP phrase
    }

    // ── Public entry point ────────────────────────────────────────────────────

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
        DPResult result = dpSolve(groups, new State(0, 0, false), memo);

        // Sort activations chronologically (DP builds them in reverse)
        List<Integer> sorted = new ArrayList<>(result.activations);
        Collections.sort(sorted);

        System.out.println("Optimizer finished.");
        System.out.println("Score: " + result.score);
        System.out.println("Activations: " + sorted);

        return new OptimalPath(sorted, result.score);
    }

    // ── DP solver ─────────────────────────────────────────────────────────────

    private static DPResult dpSolve(List<GroupInfo> groups,
                                    State state,
                                    Map<State, DPResult> memo) {

        if (state.groupIndex >= groups.size()) {
            return new DPResult(0, new ArrayList<>());
        }

        DPResult cached = memo.get(state);
        if (cached != null) return cached;

        GroupInfo group = groups.get(state.groupIndex);

        // Apply phrase gain (capped at MAX_METER)
        int meterAfterGain = Math.min(MAX_METER,
                state.starPowerMeter + (group.phraseComplete ? PHRASE_GAIN : 0));

        // Base score for this group:
        //   - 50 pts × noteCount  (chords score per note)
        //   - sustain points      (25/beat, NOT multiplied per chord note — see buildGroups)
        long groupBasePoints = (long) NOTE_POINTS * group.noteCount + group.sustainPoints;

        long bestScore = Long.MIN_VALUE;
        List<Integer> bestPath = new ArrayList<>();

        // ── Option 1: do NOT activate SP this group ──────────────────────────
        {
            boolean active = state.starPowerActive;
            int effectiveMult = group.baseMultiplier * (active ? 2 : 1);
            long score = groupBasePoints * effectiveMult;

            int meterAfterDrain = applyDrain(meterAfterGain, active, group);
            boolean activeNext  = active && meterAfterDrain > 0;

            DPResult sub = dpSolve(groups,
                    new State(state.groupIndex + 1, meterAfterDrain, activeNext), memo);

            long total = score + sub.score;
            if (total > bestScore) {
                bestScore = total;
                bestPath  = new ArrayList<>(sub.activations);
            }
        }

        // ── Option 2: activate SP at the start of this group ─────────────────
        if (!state.starPowerActive && meterAfterGain >= ACTIVATION_THRESHOLD) {

            // SP doubles the base multiplier (max effective multiplier = 8x)
            int effectiveMult = group.baseMultiplier * 2;
            long score = groupBasePoints * effectiveMult;

            int meterAfterDrain = applyDrain(meterAfterGain, true, group);
            boolean activeNext  = meterAfterDrain > 0;

            DPResult sub = dpSolve(groups,
                    new State(state.groupIndex + 1, meterAfterDrain, activeNext), memo);

            long total = score + sub.score;
            if (total > bestScore) {
                bestScore = total;
                // Prepend activation time so the list stays in order
                bestPath  = new ArrayList<>();
                bestPath.add(group.time);
                bestPath.addAll(sub.activations);
            }
        }

        DPResult best = new DPResult(bestScore, bestPath);
        memo.put(state, best);
        return best;
    }

    // ── SP drain ──────────────────────────────────────────────────────────────

    private static int applyDrain(int meter, boolean active, GroupInfo group) {
        if (!active || meter <= 0)          return meter;
        if (group.ticksPerBar <= 0 || group.deltaTicks <= 0) return meter;

        // Proportional drain: DRAIN_PER_BAR units per full measure
        int drainUnits = (DRAIN_PER_BAR * group.deltaTicks) / group.ticksPerBar;
        return Math.max(0, meter - drainUnits);
    }

    // ── Build note groups ─────────────────────────────────────────────────────

    /**
     * Groups notes that share the same tick (chords), computes base multipliers,
     * and sustain points.
     *
     * Combo multiplier rule: increments by the number of notes in the chord
     * (hitting a 3-note chord advances the combo counter by 3, not 1).
     *
     * Sustain rule: 25 pts/beat regardless of chord size — a sustain chord
     * does NOT give 25 pts × noteCount; it still gives 25 pts/beat total.
     */
    private static List<GroupInfo> buildGroups(List<Note> notes, ChartData chartData) {

        List<GroupInfo> groups = new ArrayList<>();
        int resolution = chartData.getResolution();
        int ticksPerBar = resolution * 4; // assumes 4/4 time

        // comboCount = number of individual notes hit so far (chords count per note)
        int comboCount = 0;
        int i = 0;

        while (i < notes.size()) {

            int time = notes.get(i).getTime();

            List<Note> groupNotes = new ArrayList<>();
            while (i < notes.size() && notes.get(i).getTime() == time) {
                groupNotes.add(notes.get(i));
                i++;
            }

            // Multiplier is based on combo BEFORE this group is hit
            int baseMultiplier = Math.min(4, 1 + (comboCount / 10));

            // Advance combo by the number of notes in this chord
            comboCount += groupNotes.size();

            // Sustain: 25 pts/beat for the chord, NOT per note
            // Find the longest sustain in the chord (representative; all lanes drain together)
            long sustainPoints = 0;
            long maxDuration = 0;
            for (Note note : groupNotes) {
                if (note.getDuration() > maxDuration) {
                    maxDuration = note.getDuration();
                }
            }
            if (maxDuration > 0 && resolution > 0) {
                double beats = (double) maxDuration / resolution;
                sustainPoints = (long) Math.ceil(beats * SUSTAIN_POINTS_PER_BEAT);
            }

            GroupInfo info  = new GroupInfo();
            info.time        = time;
            info.noteCount   = groupNotes.size();
            info.sustainPoints = sustainPoints;
            info.baseMultiplier = baseMultiplier;
            info.ticksPerBar = ticksPerBar;
            info.deltaTicks  = 0;        // filled in below
            info.phraseComplete = false; // filled in by caller

            groups.add(info);
        }

        // Fill deltaTicks: ticks from this group to the next (used for drain)
        for (int idx = 0; idx < groups.size() - 1; idx++) {
            groups.get(idx).deltaTicks =
                    Math.max(0, groups.get(idx + 1).time - groups.get(idx).time);
        }

        return groups;
    }

    // ── Phrase completion times ───────────────────────────────────────────────

    /**
     * Returns the tick time of the last note inside each SP phrase.
     * Completing a phrase (hitting all its notes) awards one PHRASE_GAIN.
     */
    private static Set<Integer> getPhraseCompletionTimes(List<GroupInfo> groups,
                                                          List<StarPowerPhrase> phrases) {
        Set<Integer> completionTimes = new HashSet<>();
        if (groups.isEmpty() || phrases.isEmpty()) return completionTimes;

        for (StarPowerPhrase phrase : phrases) {
            Integer lastTime = null;
            for (GroupInfo group : groups) {
                if (group.time >= phrase.getStartTick() &&
                    group.time <= phrase.getEndTick()) {
                    lastTime = group.time;
                }
            }
            if (lastTime != null) completionTimes.add(lastTime);
        }

        return completionTimes;
    }
}