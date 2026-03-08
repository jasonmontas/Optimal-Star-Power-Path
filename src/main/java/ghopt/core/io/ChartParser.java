package ghopt.core.io;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.util.List;




import ghopt.core.model.ChartData;
import ghopt.core.model.Note;
import ghopt.core.model.StarPowerPhrase;

public class ChartParser implements ChartSource {

    @Override
    public ChartData parse(File file) throws IOException {
        ChartData chartData = new ChartData();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean inExpertSingle = false;
            boolean inSongSection = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }

                if (line.equals("[Song]")) {
                    inSongSection = true;
                    inExpertSingle = false;
                    continue;
                }
                if (line.equals("[ExpertSingle]")) {
                    inExpertSingle = true;
                    inSongSection = false;
                    continue;
                }
                if (line.startsWith("[")) {
                    inExpertSingle = false;
                    inSongSection = false;
                    continue;
                }

                // Read Resolution from [Song] section
                if (inSongSection && line.startsWith("Resolution")) {
                    String[] kv = line.split("=");
                    if (kv.length == 2) {
                        try {
                            chartData.setResolution(Integer.parseInt(kv[1].trim()));
                        } catch (NumberFormatException ignored) {
                            // keep default
                        }
                    }
                    continue;
                }

                if (!inExpertSingle) {
                    continue;
                }

                // Expected lines like:  1234 = N 0 0  OR  5678 = S 2 960
                String[] parts = line.split("=");
                if (parts.length != 2) {
                    continue;
                }

                int time;
                try {
                    time = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException nfe) {
                    continue;
                }

                String[] tokens = parts[1].trim().split("\\s+");
                if (tokens.length < 3) {
                    continue;
                }

                String kind = tokens[0];
                if ("N".equals(kind)) {
                    int type;
                    int duration;
                    try {
                        type = Integer.parseInt(tokens[1]);
                        duration = Integer.parseInt(tokens[2]);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }

                    // type meanings (common): 0-4 lanes, 5 forced marker, 6 tap marker, 7 open note
                    if (type == 5 || type == 6) {
                        // Prefer notes at the SAME timestamp (applies to chords too)
                        boolean applied = applyMarkerToNotesAtTime(chartData, time, type);
                        if (!applied) {
                            // Fallback: apply to nearest earlier note-time group
                            applyMarkerToNearestEarlierGroup(chartData, time, type);
                        }
                    } else {
                        chartData.addNote(new Note(time, type, duration));
                    }
                } else if ("S".equals(kind)) {
                    // Star power phrase: "S 2 <duration>" (we only care about duration)
                    int duration;
                    try {
                        duration = Integer.parseInt(tokens[2]);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                    // Model treats phrases as [start, end) so end is start + duration
                    chartData.addStarPowerPhrase(new StarPowerPhrase(time, time + duration));
                }
            }
        }

        // Normalize ordering so later logic is predictable
        chartData.sortByTime();

        return chartData;
    }

    private static boolean applyMarkerToNotesAtTime(ChartData chartData, int time, int markerType) {
        boolean applied = false;
        List<Note> notes = chartData.getNotes();
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (n.getTime() < time) {
                break;
            }
            if (n.getTime() == time) {
                if (markerType == 5) n.setForced(true);
                if (markerType == 6) n.setTap(true);
                applied = true;
            }
        }
        return applied;
    }

    private static void applyMarkerToNearestEarlierGroup(ChartData chartData, int time, int markerType) {
        // Find nearest earlier timestamp among notes, then apply to all notes at that timestamp
        List<Note> notes = chartData.getNotes();
        int nearest = -1;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (n.getTime() <= time) {
                nearest = n.getTime();
                break;
            }
        }
        if (nearest < 0) {
            return;
        }
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (n.getTime() < nearest) {
                break;
            }
            if (n.getTime() == nearest) {
                if (markerType == 5) n.setForced(true);
                if (markerType == 6) n.setTap(true);
            }
        }
    }

    

    
}
