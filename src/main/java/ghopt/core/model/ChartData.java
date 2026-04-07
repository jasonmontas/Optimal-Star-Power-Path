package ghopt.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * In-memory representation of a parsed chart (notes + star power phrases + tempo map).
 * All times are in chart ticks (based on resolution = ticks per quarter note).
 */
public final class ChartData {

    private int resolution = 480;
    // Song offset in seconds from the [Song] section (can be negative)
    private double offset = 0.0;

    private final List<Note> notes = new ArrayList<>();
    private final List<StarPowerPhrase> starPowerPhrases = new ArrayList<>();
    private final List<TempoEvent> tempoEvents = new ArrayList<>();
    private final List<TimeSignatureEvent> timeSignatureEvents = new ArrayList<>();

    public ChartData() {}

    public ChartData(int resolution) {
        setResolution(resolution);
    }

    public int getResolution() { return resolution; }

    public void setResolution(int resolution) {
        if (resolution <= 0) throw new IllegalArgumentException("resolution must be > 0");
        this.resolution = resolution;
    }

    public double getOffset() { return offset; }
    public void setOffset(double offset) { this.offset = offset; }

    public List<Note> getNotes() { return notes; }
    public List<StarPowerPhrase> getStarPowerPhrases() { return starPowerPhrases; }
    public List<TempoEvent> getTempoEvents() { return tempoEvents; }
    public List<TimeSignatureEvent> getTimeSignatureEvents() { return timeSignatureEvents; }

    public void addNote(Note note) {
        notes.add(Objects.requireNonNull(note, "note"));
    }

    public void addStarPowerPhrase(StarPowerPhrase phrase) {
        starPowerPhrases.add(Objects.requireNonNull(phrase, "phrase"));
    }

    public void addTempoEvent(TempoEvent event) {
        tempoEvents.add(Objects.requireNonNull(event, "event"));
    }

    public void addTimeSignatureEvent(TimeSignatureEvent event) {
        timeSignatureEvents.add(Objects.requireNonNull(event, "event"));
    }

    public void sortByTime() {
        notes.sort(Comparator.comparingInt(Note::getTime));
        starPowerPhrases.sort(Comparator.comparingInt(StarPowerPhrase::getStartTick));
        tempoEvents.sort(Comparator.comparingInt(TempoEvent::getTick));
        timeSignatureEvents.sort(Comparator.comparingInt(TimeSignatureEvent::getTick));
    }

    public int maxTick() {
        int max = 0;
        for (Note n : notes) max = Math.max(max, n.endTime());
        for (StarPowerPhrase p : starPowerPhrases) max = Math.max(max, p.getEndTick());
        return max;
    }

    public List<Note> notesView() { return Collections.unmodifiableList(notes); }
    public List<StarPowerPhrase> starPowerPhrasesView() { return Collections.unmodifiableList(starPowerPhrases); }
    public List<TempoEvent> tempoEventsView() { return Collections.unmodifiableList(tempoEvents); }
    public List<TimeSignatureEvent> timeSignatureEventsView() { return Collections.unmodifiableList(timeSignatureEvents); }

    @Override
    public String toString() {
        return "ChartData{resolution=" + resolution +
                ", notes=" + notes.size() +
                ", starPowerPhrases=" + starPowerPhrases.size() +
                ", tempoEvents=" + tempoEvents.size() +
                ", timeSignatureEvents=" + timeSignatureEvents.size() + '}';
    }
}