package ghopt.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * In-memory representation of a parsed chart (notes + star power phrases).
 *
 * All times are in chart ticks (based on resolution = ticks per quarter note).
 */
public final class ChartData {

    /** Ticks per quarter note from the chart's [Song] section (Resolution). */
    private int resolution = 480;

    private final List<Note> notes = new ArrayList<>();
    private final List<StarPowerPhrase> starPowerPhrases = new ArrayList<>();

    public ChartData() {}

    public ChartData(int resolution) {
        setResolution(resolution);
    }

    public int getResolution() {
        return resolution;
    }

    public void setResolution(int resolution) {
        if (resolution <= 0) {
            throw new IllegalArgumentException("resolution must be > 0");
        }
        this.resolution = resolution;
    }

    /** Mutable list of notes (tick domain). */
    public List<Note> getNotes() {
        return notes;
    }

    /** Mutable list of star power phrases (tick domain). */
    public List<StarPowerPhrase> getStarPowerPhrases() {
        return starPowerPhrases;
    }

    public void addNote(Note note) {
        notes.add(Objects.requireNonNull(note, "note"));
    }

    public void addStarPowerPhrase(StarPowerPhrase phrase) {
        starPowerPhrases.add(Objects.requireNonNull(phrase, "phrase"));
    }

    /** Sort notes + phrases by start tick (call after parsing). */
    public void sortByTime() {
        notes.sort(Comparator.comparingInt(Note::getTime));
        starPowerPhrases.sort(Comparator.comparingInt(StarPowerPhrase::getStartTick));
    }

    /** @return max tick reached by any note end or phrase end. */
    public int maxTick() {
        int max = 0;

        for (Note n : notes) {
            max = Math.max(max, n.endTime());
        }
        for (StarPowerPhrase p : starPowerPhrases) {
            max = Math.max(max, p.getEndTick());
        }

        return max;
    }

    /** Immutable views (nice for algorithms later). */
    public List<Note> notesView() {
        return Collections.unmodifiableList(notes);
    }

    public List<StarPowerPhrase> starPowerPhrasesView() {
        return Collections.unmodifiableList(starPowerPhrases);
    }

    @Override
    public String toString() {
        return "ChartData{" +
                "resolution=" + resolution +
                ", notes=" + notes.size() +
                ", starPowerPhrases=" + starPowerPhrases.size() +
                '}';
    }
}