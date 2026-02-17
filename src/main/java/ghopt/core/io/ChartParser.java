package ghopt.core.io;
 
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import javax.sound.midi.*;
import java.awt.geom.GeneralPath;

public class ChartParser {

    public static class Note {
        public int time;
        public int type; // original type value from chart
        public int duration;
        public boolean forced;
        public boolean tap;
        public boolean open;

        public Note(int time, int type, int duration) {
            this.time = time;
            this.type = type;
            this.duration = duration;
            this.forced = false;
            this.tap = false;
            this.open = (type == 7);
        }

        @Override
        public String toString() {
            return "Note{" +
                    "time=" + time +
                    ", type=" + type +
                    ", duration=" + duration +
                    ", forced=" + forced +
                    ", tap=" + tap +
                    ", open=" + open +
                    '}';
        }
    }

    public static class StarPowerPhrase {
        public int start;
        public int end;

        public StarPowerPhrase(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "StarPowerPhrase{" +
                    "start=" + start +
                    ", end=" + end +
                    '}';
        }
    }

    public static class TempoEvent {
        public int time;
        public int microsecondsPerQuarter;

        public TempoEvent(int time, int microsecondsPerQuarter) {
            this.time = time;
            this.microsecondsPerQuarter = microsecondsPerQuarter;
        }

        @Override
        public String toString() {
            return "TempoEvent{" +
                    "time=" + time +
                    ", microsecondsPerQuarter=" + microsecondsPerQuarter +
                    '}';
        }
    }

    public static class TimeSignatureEvent {
        public int time;
        public int numerator;
        public int denominator;

        public TimeSignatureEvent(int time, int numerator, int denominator) {
            this.time = time;
            this.numerator = numerator;
            this.denominator = denominator;
        }

        @Override
        public String toString() {
            return "TimeSignatureEvent{" +
                    "time=" + time +
                    ", numerator=" + numerator +
                    ", denominator=" + denominator +
                    '}';
        }
    }

    public static class ChartData {
        public List<Note> notes = new ArrayList<>();
        public List<StarPowerPhrase> starPowerPhrases = new ArrayList<>();
        public List<TempoEvent> tempoEvents = new ArrayList<>();
        public List<TimeSignatureEvent> timeSignatures = new ArrayList<>();
        public int resolution = 480;
    }

    public static ChartData parseChart(String filePath) throws IOException {
        if (filePath.toLowerCase().endsWith(".mid")) {
            return parseMidiChart(filePath);
        }

        ChartData chartData = new ChartData();
        int resolution = 480;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean inExpertSingle = false;
            boolean inSongSection = false;
            boolean inSyncTrack = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.equals("[Song]")) {
                    inSongSection = true;
                    inSyncTrack = false;
                } else if (line.equals("[ExpertSingle]")) {
                    inExpertSingle = true;
                    inSyncTrack = false;
                } else if (line.equals("[SyncTrack]")) {
                    inSyncTrack = true;
                    inExpertSingle = false;
                    inSongSection = false;
                } else if (line.startsWith("[")) {
                    inExpertSingle = false;
                    inSongSection = false;
                    inSyncTrack = false;
                }

                if (inSongSection && line.startsWith("Resolution")) {
                    String[] kv = line.split("=");
                    if (kv.length == 2) {
                        try {
                            resolution = Integer.parseInt(kv[1].trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }

                if (inSyncTrack) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        int time = Integer.parseInt(parts[0].trim());
                        String[] syncData = parts[1].trim().split(" ");

                        if (syncData.length >= 2) {
                            if (syncData[0].equals("B")) {
                                try {
                                    int mpq = Integer.parseInt(syncData[1]);
                                    chartData.tempoEvents.add(new TempoEvent(time, mpq));
                                } catch (NumberFormatException ignored) {}
                            } else if (syncData[0].equals("TS")) {
                                try {
                                    int numerator = Integer.parseInt(syncData[1]);
                                    int denominator = syncData.length >= 3
                                            ? Integer.parseInt(syncData[2])
                                            : 4;
                                    chartData.timeSignatures.add(new TimeSignatureEvent(time, numerator, denominator));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }

                if (inExpertSingle) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        int time = Integer.parseInt(parts[0].trim());
                        String[] noteData = parts[1].trim().split(" ");

                        if (noteData.length >= 3) {
                            if (noteData[0].equals("N")) {
                                int type = Integer.parseInt(noteData[1]);
                                int duration = Integer.parseInt(noteData[2]);

                                if (type == 5 || type == 6) {
                                    for (int i = chartData.notes.size() - 1; i >= 0; i--) {
                                        Note prev = chartData.notes.get(i);
                                        if (prev.time <= time) {
                                            if (type == 5) prev.forced = true;
                                            else prev.tap = true;
                                            break;
                                        }
                                    }
                                } else {
                                    chartData.notes.add(new Note(time, type, duration));
                                }
                            } else if (noteData[0].equals("S")) {
                                int duration = Integer.parseInt(noteData[2]);
                                chartData.starPowerPhrases.add(new StarPowerPhrase(time, time + duration));
                            }
                        }
                    }
                }
            }
        }
        chartData.resolution = resolution;
        chartData.tempoEvents.sort((a, b) -> Integer.compare(a.time, b.time));
        chartData.timeSignatures.sort((a, b) -> Integer.compare(a.time, b.time));
        if (chartData.timeSignatures.isEmpty()) {
            chartData.timeSignatures.add(new TimeSignatureEvent(0, 4, 4));
        } else if (chartData.timeSignatures.get(0).time != 0) {
            chartData.timeSignatures.add(0, new TimeSignatureEvent(0, 4, 4));
        }
        return chartData;
    }

    private static ChartData parseMidiChart(String filePath) throws IOException {
        ChartData chartData = new ChartData();
        try {
            Sequence sequence = MidiSystem.getSequence(new File(filePath));
            chartData.resolution = sequence.getResolution();

            Track[] tracks = sequence.getTracks();
            for (Track track : tracks) {
                String trackName = getTrackName(track);
                boolean isGuitarTrack = isGuitarTrackName(trackName);
                Map<Integer, Integer> activeNotes = new HashMap<>();

                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);
                    MidiMessage message = event.getMessage();

                    if (message instanceof MetaMessage) {
                        MetaMessage meta = (MetaMessage) message;
                        int type = meta.getType();
                        byte[] data = meta.getData();

                        if (type == 0x51 && data.length == 3) {
                            int mpq = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                            chartData.tempoEvents.add(new TempoEvent((int) event.getTick(), mpq));
                        } else if (type == 0x58 && data.length >= 2) {
                            int numerator = data[0] & 0xFF;
                            int denominator = 1 << (data[1] & 0xFF);
                            chartData.timeSignatures.add(new TimeSignatureEvent((int) event.getTick(), numerator, denominator));
                        }
                    } else if (message instanceof ShortMessage && isGuitarTrack) {
                        ShortMessage sm = (ShortMessage) message;
                        int cmd = sm.getCommand();
                        int note = sm.getData1();
                        int velocity = sm.getData2();
                        int tick = (int) event.getTick();

                        boolean noteOn = cmd == ShortMessage.NOTE_ON && velocity > 0;
                        boolean noteOff = cmd == ShortMessage.NOTE_OFF || (cmd == ShortMessage.NOTE_ON && velocity == 0);

                        if (noteOn) {
                            activeNotes.put(note, tick);
                        } else if (noteOff && activeNotes.containsKey(note)) {
                            int start = activeNotes.remove(note);
                            int duration = Math.max(0, tick - start);

                            if (note >= 96 && note <= 100) {
                                int type = note - 96;
                                chartData.notes.add(new Note(start, type, duration));
                            } else if (note == 116) {
                                chartData.starPowerPhrases.add(new StarPowerPhrase(start, start + duration));
                            } else if (note == 106) {
                                chartData.notes.add(new Note(start, 7, duration));
                            }
                        }
                    }
                }
            }

            chartData.tempoEvents.sort((a, b) -> Integer.compare(a.time, b.time));
            chartData.timeSignatures.sort((a, b) -> Integer.compare(a.time, b.time));
            if (chartData.timeSignatures.isEmpty()) {
                chartData.timeSignatures.add(new TimeSignatureEvent(0, 4, 4));
            } else if (chartData.timeSignatures.get(0).time != 0) {
                chartData.timeSignatures.add(0, new TimeSignatureEvent(0, 4, 4));
            }
        } catch (InvalidMidiDataException e) {
            throw new IOException("Invalid MIDI file: " + e.getMessage(), e);
        }

        return chartData;
    }

    private static String getTrackName(Track track) {
        for (int i = 0; i < track.size(); i++) {
            MidiEvent event = track.get(i);
            MidiMessage message = event.getMessage();
            if (message instanceof MetaMessage) {
                MetaMessage meta = (MetaMessage) message;
                if (meta.getType() == 0x03) {
                    byte[] data = meta.getData();
                    return new String(data, StandardCharsets.US_ASCII).trim();
                }
            }
        }
        return "";
    }

    private static boolean isGuitarTrackName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.trim().toUpperCase();
        return normalized.equals("PART GUITAR") ||
               normalized.equals("PART GUITAR COOP") ||
               normalized.equals("T1 GEMS");
    }

    public static void generateChartImage(ChartData chartData, String outputFilePath) throws IOException {
        generateChartImage(chartData, outputFilePath, null);
    }

    public static void generateChartImage(ChartData chartData, String outputFilePath, 
                                          List<Integer> activationTimes) throws IOException {
        // Create output directory if it doesn't exist
        File outputFile = new File(outputFilePath);
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs();
        }

        int width = 4000; // Increased width to make the image wider
        int heightPerLayer = 600; // Reduced height per layer to make the image less tall
        int margin = 50;
        int noteSize = 20;
        int laneHeight = (heightPerLayer - 2 * margin) / 5;
        int timeScale = 2;

        int maxTime = chartData.notes.stream().mapToInt(note -> note.time).max().orElse(0);
        int totalLayers = (maxTime / (width * timeScale)) + 1;
        int totalHeight = totalLayers * heightPerLayer;

        BufferedImage image = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, totalHeight);

        g.setColor(Color.LIGHT_GRAY);
        for (int layer = 0; layer < totalLayers; layer++) {
            int layerOffset = layer * heightPerLayer;
            for (int i = 0; i <= 5; i++) {
                int y = layerOffset + margin + i * laneHeight;
                g.drawLine(margin, y, width - margin, y);
            }
        }

        g.setColor(Color.GRAY);
        for (int layer = 0; layer < totalLayers; layer++) {
            int layerOffset = layer * heightPerLayer;
            for (int t = 0; t < width; t += 200) {
                int x = margin + t;
                int yStart = layerOffset + margin;
                int yEnd = layerOffset + heightPerLayer - margin;
                g.drawLine(x, yStart, x, yEnd);
                g.drawString(String.valueOf((layer * width + t) * timeScale), x, yStart - 10);
            }
        }

        g.setColor(new Color(173, 216, 230, 128));
        for (StarPowerPhrase phrase : chartData.starPowerPhrases) {
            int startLayer = (phrase.start / (width * timeScale));
            int endLayer = (phrase.end / (width * timeScale));

            for (int layer = startLayer; layer <= endLayer; layer++) {
                int layerOffset = layer * heightPerLayer;
                int xStart = margin + (layer == startLayer ? (phrase.start % (width * timeScale)) / timeScale : 0);
                int xEnd = margin + (layer == endLayer ? (phrase.end % (width * timeScale)) / timeScale : width);
                g.fillRect(xStart, layerOffset + margin, xEnd - xStart, heightPerLayer - 2 * margin);
            }
        }

        // Draw activation times highlighted in green
        if (activationTimes != null && !activationTimes.isEmpty()) {
            g.setColor(new Color(0, 255, 0, 64)); // Green with transparency
            for (int activationTime : activationTimes) {
                int layer = activationTime / (width * timeScale);
                int layerOffset = layer * heightPerLayer;
                int xPos = margin + (activationTime % (width * timeScale)) / timeScale;
                int activationWidth = 100; // Width of activation highlight
                g.fillRect(xPos - activationWidth / 2, layerOffset + margin, activationWidth, heightPerLayer - 2 * margin);
                
                // Draw bright green border
                g.setColor(Color.GREEN);
                g.setStroke(new java.awt.BasicStroke(3));
                g.drawRect(xPos - activationWidth / 2, layerOffset + margin, activationWidth, heightPerLayer - 2 * margin);
                g.setColor(new Color(0, 255, 0, 64)); // Reset to transparent green
            }
        }

        // Draw notes with colors based on type across layers, including sustains
        Color[] noteColors = {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE, Color.ORANGE}; // types 0-4
        Color openColor = Color.MAGENTA; // open note color (type 7)
        for (Note note : chartData.notes) {
            int layer = note.time / (width * timeScale);
            int layerOffset = layer * heightPerLayer;
            int x = margin + (note.time % (width * timeScale)) / timeScale;

            boolean inStarPower = chartData.starPowerPhrases.stream()
                    .anyMatch(phrase -> note.time >= phrase.start && note.time <= phrase.end);

            if (note.open) {
                int barWidth = Math.max(4, noteSize / 2);
                int barX = x - barWidth / 2;
                int barY = layerOffset + margin;
                int barHeightFull = heightPerLayer - 2 * margin;
                Color oc = inStarPower ? openColor.brighter() : openColor;
                g.setColor(oc);
                g.fillRect(barX, barY, barWidth, barHeightFull);

                if (inStarPower) {
                    Color borderColor = new Color(0, 0, 139);
                    Stroke oldStroke = g.getStroke();
                    Color oldColor = g.getColor();
                    g.setColor(borderColor);
                    g.setStroke(new BasicStroke(2));
                    g.drawRect(barX, barY, barWidth, barHeightFull);
                    g.setStroke(oldStroke);
                    g.setColor(oldColor);
                }

                if (note.duration > 0) {
                    int sustainEndX = margin + ((note.time + note.duration) % (width * timeScale)) / timeScale;
                    int tailY = barY + barHeightFull / 2 - 2;
                    int tailXStart = x + barWidth / 2;
                    int tailWidth = Math.max(1, sustainEndX - tailXStart);
                    g.fillRect(tailXStart, tailY, tailWidth, 4);
                }

                if (note.forced) {
                    Color oldColor = g.getColor();
                    Stroke oldStroke = g.getStroke();
                    g.setColor(Color.BLACK);
                    g.setStroke(new BasicStroke(2));
                    g.drawRect(barX, barY, barWidth, barHeightFull);
                    g.setStroke(oldStroke);
                    g.setColor(oldColor);
                }

                if (note.tap) {
                    int tickY = barY + barHeightFull / 2;
                    g.setColor(Color.WHITE);
                    g.fillRect(x - 2, tickY - 2, 4, 4);
                }
            } else {
                int lane = note.type;
                if (lane < 0 || lane > 4) continue;

                int y = layerOffset + margin + lane * laneHeight + laneHeight / 2 - noteSize / 2;
                Color col = inStarPower ? noteColors[lane].brighter() : noteColors[lane];

                if (inStarPower) {
                    int cx = x + noteSize / 2;
                    int cy = y + noteSize / 2;
                    int outer = Math.max(8, noteSize * 3 / 4);
                    int inner = Math.max(4, noteSize / 3);
                    Shape star = createStar(cx, cy, outer, inner, 5);
                    g.setColor(col);
                    g.fill(star);

                    Color outline = col.darker();
                    Stroke oldStroke = g.getStroke();
                    Color oldColor = g.getColor();
                    g.setColor(outline);
                    g.setStroke(new BasicStroke(2));
                    g.draw(star);
                    g.setStroke(oldStroke);
                    g.setColor(oldColor);
                } else {
                    g.setColor(col);
                    g.fillOval(x, y, noteSize, noteSize);
                }

                if (note.duration > 0) {
                    int sustainEndX = margin + ((note.time + note.duration) % (width * timeScale)) / timeScale;
                    int sustainWidth = sustainEndX - x;
                    if (sustainWidth > 0) {
                        Color oldColor = g.getColor();
                        g.setColor(col);
                        g.fillRect(x + noteSize / 2 - 2, y + noteSize / 2 - 2, sustainWidth, 4);
                        g.setColor(oldColor);
                    }
                }
            }
        }

        g.dispose();
        ImageIO.write(image, "png", new File(outputFilePath));
    }

    private static Shape createStar(int cx, int cy, int outerRadius, int innerRadius, int points) {
        GeneralPath path = new GeneralPath();
        double angle = -Math.PI / 2;
        double step = Math.PI / points;

        for (int i = 0; i < points * 2; i++) {
            double r = (i % 2 == 0) ? outerRadius : innerRadius;
            double px = cx + Math.cos(angle) * r;
            double py = cy + Math.sin(angle) * r;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
            angle += step;
        }
        path.closePath();
        return path;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ghopt.core.io.ChartParser <chart-file-path> <output-image-path>");
            System.out.println("Example: java ghopt.core.io.ChartParser resources/Song/notes.chart output.png");
            return;
        }

        String chartPath = args[0];
        String outputPath = args[1];

        try {
            ChartData chartData = parseChart(chartPath);
            generateChartImage(chartData, outputPath);
            System.out.println("Chart image generated at: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
