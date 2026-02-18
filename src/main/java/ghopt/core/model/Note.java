package ghopt.core.model;

/**
 * A single note event in a Clone Hero / Guitar Hero style chart.
 *
 * Time and duration are in chart ticks.
 * type meanings (Clone Hero .chart common):
 *   0-4 = fret lanes (green..orange)
 *   7   = open note
 *
 * forced/tap are note modifiers that may be represented as separate marker events
 * in the chart file and applied to the nearest note group.
 */
public class Note {

    /** Tick timestamp where the note starts. */
    private final int time;

    /** Note type / lane (0-4 frets, 7=open). */
    private final int type;

    /** Sustain duration in ticks (0 for no sustain). */
    private final int duration;

    private boolean forced;
    private boolean tap;

    public Note(int time, int type, int duration) {
        this.time = time;
        this.type = type;
        this.duration = Math.max(0, duration);
        this.forced = false;
        this.tap = false;
    }

    public int getTime() {
        return time;
    }

    public int getType() {
        return type;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isForced() {
        return forced;
    }

    public void setForced(boolean forced) {
        this.forced = forced;
    }

    public boolean isTap() {
        return tap;
    }

    public void setTap(boolean tap) {
        this.tap = tap;
    }

    public boolean isOpen() {
        return type == 7;
    }

    /** Tick timestamp where the note ends (time + duration). */
    public int endTime() {
        return time + duration;
    }

    @Override
    public String toString() {
        return "Note{" +
                "time=" + time +
                ", type=" + type +
                ", duration=" + duration +
                ", forced=" + forced +
                ", tap=" + tap +
                ", open=" + isOpen() +
                '}';
    }
}
