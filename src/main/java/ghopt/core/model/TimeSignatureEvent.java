package ghopt.core.model;

/**
 * A time signature change event.
 *
 * denominator is the musical denominator value (for example, 4 for x/4).
 */
public final class TimeSignatureEvent {

    private final int tick;
    private final int numerator;
    private final int denominator;

    public TimeSignatureEvent(int tick, int numerator, int denominator) {
        this.tick = tick;
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public int getTick() { return tick; }
    public int getNumerator() { return numerator; }
    public int getDenominator() { return denominator; }

    @Override
    public String toString() {
        return "TimeSignatureEvent{tick=" + tick + ", numerator=" + numerator + ", denominator=" + denominator + "}";
    }
}