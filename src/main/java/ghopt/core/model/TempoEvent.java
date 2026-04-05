package ghopt.core.model;

/**
 * A BPM change event from the [SyncTrack] section of a .chart file.
 * The chart format stores tempo as microseconds per beat (B event).
 */
public final class TempoEvent {

    private final int tick;
    private final double bpm;

    public TempoEvent(int tick, double bpm) {
        this.tick = tick;
        this.bpm  = bpm;
    }

    public int getTick() { return tick; }
    public double getBpm() { return bpm; }

    @Override
    public String toString() {
        return "TempoEvent{tick=" + tick + ", bpm=" + bpm + "}";
    }
}