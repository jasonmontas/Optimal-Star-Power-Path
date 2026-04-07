package ghopt.core.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import ghopt.core.model.ChartData;
import ghopt.core.model.Note;
import ghopt.core.model.StarPowerPhrase;
import ghopt.core.model.TempoEvent;
import ghopt.core.model.TimeSignatureEvent;

public class ChartParser implements ChartSource {

    @Override
    public ChartData parse(File file, String difficulty) throws IOException {
        ChartData chartData = new ChartData();

        // Build the section name from the difficulty, e.g. "Expert" -> "[ExpertSingle]"
        String targetSection = "[" + difficulty + "Single]";

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean inNoteSection = false;
            boolean inSongSection  = false;
            boolean inSyncTrack    = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) continue;

                // Section headers
                if (line.equals("[Song]")) {
                    inSongSection  = true;
                    inNoteSection  = false;
                    inSyncTrack    = false;
                    continue;
                }
                if (line.equals("[SyncTrack]")) {
                    inSyncTrack    = true;
                    inSongSection  = false;
                    inNoteSection  = false;
                    continue;
                }
                if (line.equals(targetSection)) {
                    inNoteSection  = true;
                    inSongSection  = false;
                    inSyncTrack    = false;
                    continue;
                }
                if (line.startsWith("[")) {
                    inNoteSection  = false;
                    inSongSection  = false;
                    inSyncTrack    = false;
                    continue;
                }

                // [Song] section: read Resolution and Offset
                if (inSongSection && line.startsWith("Resolution")) {
                    String[] kv = line.split("=");
                    if (kv.length == 2) {
                        try {
                            chartData.setResolution(Integer.parseInt(kv[1].trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                    continue;
                }
                if (inSongSection && line.startsWith("Offset")) {
                    String[] kv = line.split("=");
                    if (kv.length == 2) {
                        try {
                            chartData.setOffset(Double.parseDouble(kv[1].trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                    continue;
                }

                // [SyncTrack] section: read BPM events (B events)
                // Format: <tick> = B <microsecondsPerBeat>
                if (inSyncTrack) {
                    String[] parts = line.split("=");
                    if (parts.length != 2) continue;
                    int tick;
                    try {
                        tick = Integer.parseInt(parts[0].trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    String[] tokens = parts[1].trim().split("\\s+");
                    if (tokens.length >= 2 && "B".equals(tokens[0])) {
                        try {
                            long microsecondsPerBeat = Long.parseLong(tokens[1]);
                            double bpm = 60_000_000.0 / microsecondsPerBeat;
                            chartData.addTempoEvent(new TempoEvent(tick, bpm));
                        } catch (NumberFormatException ignored) {}
                    } else if (tokens.length >= 2 && "TS".equals(tokens[0])) {
                        try {
                            int numerator = Integer.parseInt(tokens[1]);
                            int denominator = (tokens.length >= 3) ? Integer.parseInt(tokens[2]) : 4;
                            chartData.addTimeSignatureEvent(new TimeSignatureEvent(tick, numerator, denominator));
                        } catch (NumberFormatException ignored) {}
                    }
                    continue;
                }

                if (!inNoteSection) continue;

                // [ExpertSingle] section: read notes and SP phrases
                String[] parts = line.split("=");
                if (parts.length != 2) continue;

                int time;
                try {
                    time = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException nfe) {
                    continue;
                }

                String[] tokens = parts[1].trim().split("\\s+");
                if (tokens.length < 3) continue;

                String kind = tokens[0];
                if ("N".equals(kind)) {
                    int type, duration;
                    try {
                        type     = Integer.parseInt(tokens[1]);
                        duration = Integer.parseInt(tokens[2]);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                    if (type == 5 || type == 6) {
                        boolean applied = applyMarkerToNotesAtTime(chartData, time, type);
                        if (!applied) applyMarkerToNearestEarlierGroup(chartData, time, type);
                    } else {
                        chartData.addNote(new Note(time, type, duration));
                    }
                } else if ("S".equals(kind)) {
                    int duration;
                    try {
                        duration = Integer.parseInt(tokens[2]);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                    chartData.addStarPowerPhrase(new StarPowerPhrase(time, time + duration));
                }
            }
        }

        chartData.sortByTime();
        if (chartData.getTimeSignatureEvents().isEmpty()) {
            chartData.addTimeSignatureEvent(new TimeSignatureEvent(0, 4, 4));
        } else if (chartData.getTimeSignatureEvents().get(0).getTick() != 0) {
            chartData.getTimeSignatureEvents().add(0, new TimeSignatureEvent(0, 4, 4));
        }
        return chartData;
    }

    private static boolean applyMarkerToNotesAtTime(ChartData chartData, int time, int markerType) {
        boolean applied = false;
        List<Note> notes = chartData.getNotes();
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (n.getTime() < time) break;
            if (n.getTime() == time) {
                if (markerType == 5) n.setForced(true);
                if (markerType == 6) n.setTap(true);
                applied = true;
            }
        }
        return applied;
    }

    private static void applyMarkerToNearestEarlierGroup(ChartData chartData, int time, int markerType) {
        List<Note> notes = chartData.getNotes();
        int nearest = -1;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (n.getTime() <= time) { nearest = n.getTime(); break; }
        }
        if (nearest < 0) return;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (n.getTime() < nearest) break;
            if (n.getTime() == nearest) {
                if (markerType == 5) n.setForced(true);
                if (markerType == 6) n.setTap(true);
            }
        }
    }
}