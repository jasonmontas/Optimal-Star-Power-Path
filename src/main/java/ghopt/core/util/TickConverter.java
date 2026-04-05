package ghopt.core.util;

import ghopt.core.model.ChartData;
import ghopt.core.model.TempoEvent;
import java.util.List;

/**
 * Converts chart ticks to wall-clock time using the tempo map from [SyncTrack].
 * Handles tempo changes mid-song correctly.
 */
public class TickConverter {

    /**
     * Converts a tick position to seconds, respecting all tempo changes.
     *
     * @param tick       the tick to convert
     * @param chartData  chart containing the tempo map and resolution
     * @return time in seconds from the start of the song
     */
    public static double tickToSeconds(int tick, ChartData chartData) {
        List<TempoEvent> tempoEvents = chartData.getTempoEvents();
        int resolution = chartData.getResolution();

        // If no tempo map, fall back to 120 BPM
        if (tempoEvents.isEmpty()) {
            return (double) tick / resolution / 2.0;
        }

        double seconds = 0.0;
        int prevTick = 0;
        double prevBpm = tempoEvents.get(0).getBpm();

        for (TempoEvent event : tempoEvents) {
            if (event.getTick() >= tick) break;

            int deltaTicks = event.getTick() - prevTick;
            seconds += ticksToSeconds(deltaTicks, prevBpm, resolution);

            prevTick = event.getTick();
            prevBpm  = event.getBpm();
        }

        // Remaining ticks after last tempo event
        seconds += ticksToSeconds(tick - prevTick, prevBpm, resolution);

        // Subtract song offset (from [Song] Offset field)
        seconds -= chartData.getOffset();

        return seconds;
    }

    private static double ticksToSeconds(int ticks, double bpm, int resolution) {
        if (ticks <= 0 || bpm <= 0) return 0.0;
        double beatsPerSecond = bpm / 60.0;
        double beats = (double) ticks / resolution;
        return beats / beatsPerSecond;
    }

    /**
     * Formats seconds as "m:ss.t" (e.g. "1:23.4").
     */
    public static String formatTimestamp(double seconds) {
        int mins = (int) (seconds / 60);
        double secs = seconds % 60;
        return String.format("%d:%04.1f", mins, secs);
    }
}