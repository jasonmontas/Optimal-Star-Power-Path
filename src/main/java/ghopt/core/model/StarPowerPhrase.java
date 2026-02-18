package ghopt.core.model;

/**
 * Represents a Star Power phrase window in ticks, inclusive start and exclusive end.
 * (We treat it as a half-open interval: [startTick, endTick))
 */
public final class StarPowerPhrase {

    private final int startTick;
    private final int endTick;

    public StarPowerPhrase(int startTick, int endTick) {
        if (startTick < 0) {
            throw new IllegalArgumentException("startTick must be >= 0");
        }
        if (endTick < startTick) {
            throw new IllegalArgumentException("endTick must be >= startTick");
        }
        this.startTick = startTick;
        this.endTick = endTick;
    }

    public int getStartTick() {
        return startTick;
    }

    public int getEndTick() {
        return endTick;
    }

    public int lengthTicks() {
        return endTick - startTick;
    }

    /** Returns true if the tick is within the phrase window. */
    public boolean containsTick(int tick) {
        return tick >= startTick && tick < endTick;
    }

    /** Returns true if this phrase overlaps another phrase. */
    public boolean overlaps(StarPowerPhrase other) {
        return this.startTick < other.endTick && other.startTick < this.endTick;
    }

    @Override
    public String toString() {
        return "StarPowerPhrase{" +
                "startTick=" + startTick +
                ", endTick=" + endTick +
                '}';
    }
}
