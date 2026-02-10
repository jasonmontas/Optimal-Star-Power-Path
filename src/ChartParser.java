import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.ArrayList;
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

    public static class ChartData {
        public List<Note> notes = new ArrayList<>();
        public List<StarPowerPhrase> starPowerPhrases = new ArrayList<>();
    }

    public static ChartData parseChart(String filePath) throws IOException {
        ChartData chartData = new ChartData();
        int resolution = 480; // default resolution (ticks per quarter note)
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean inExpertSingle = false;
            boolean inSongSection = false; // track when inside [Song] to read Resolution


            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.equals("[Song]")) {
                    inSongSection = true;
                } else if (line.equals("[ExpertSingle]")) {
                    inExpertSingle = true;
                } else if (line.startsWith("[")) {
                    inExpertSingle = false;
                    inSongSection = false;
                }

                // Read Resolution from [Song] section
                if (inSongSection && line.startsWith("Resolution")) {
                    String[] kv = line.split("=");
                    if (kv.length == 2) {
                        try {
                            resolution = Integer.parseInt(kv[1].trim());
                        } catch (NumberFormatException ignored) {}
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
                                // type meanings: 0-4 = lanes, 5 = forced marker, 6 = tap marker, 7 = open note
                                if (type == 5 || type == 6) {
                                    // mark the previous note (if any) as forced or tap
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
        return chartData;
    }

    public static void generateChartImage(ChartData chartData, String outputFilePath) throws IOException {
        int width = 4000; // Increased width to make the image wider
        int heightPerLayer = 600; // Reduced height per layer to make the image less tall
        int margin = 50;
        int noteSize = 20;
        int laneHeight = (heightPerLayer - 2 * margin) / 5; // Adjusted for 5 lanes (including open notes)
        int timeScale = 2; // Adjusted time scale to fit more time horizontally

        // Calculate total height based on the song duration
        int maxTime = chartData.notes.stream().mapToInt(note -> note.time).max().orElse(0);
        int totalLayers = (maxTime / (width * timeScale)) + 1;
        int totalHeight = totalLayers * heightPerLayer;

        BufferedImage image = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Enable anti-aliasing for smoother graphics
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, totalHeight);

        // Draw grid lines for lanes in each layer
        g.setColor(Color.LIGHT_GRAY);
        for (int layer = 0; layer < totalLayers; layer++) {
            int layerOffset = layer * heightPerLayer;
            for (int i = 0; i <= 5; i++) { // Adjusted for 5 lanes
                int y = layerOffset + margin + i * laneHeight;
                g.drawLine(margin, y, width - margin, y);
            }
        }

        // Draw time markers in each layer
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

        // Draw star power phrases across layers
        g.setColor(new Color(173, 216, 230, 128)); // Light blue with transparency
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

        // Draw notes with colors based on type across layers, including sustains
        Color[] noteColors = {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE, Color.ORANGE}; // types 0-4
        Color openColor = Color.MAGENTA; // open note color (type 7)
        for (Note note : chartData.notes) {
            int layer = note.time / (width * timeScale);
            int layerOffset = layer * heightPerLayer;
            int x = margin + (note.time % (width * timeScale)) / timeScale; // Scale time horizontally

            boolean inStarPower = chartData.starPowerPhrases.stream()
                    .anyMatch(phrase -> note.time >= phrase.start && note.time <= phrase.end);

            if (note.open) {
                // Open note: vertical purple bar spanning the playable lanes
                int barWidth = Math.max(4, noteSize / 2);
                int barX = x - barWidth / 2;
                int barY = layerOffset + margin; // span from top to bottom of playable area in layer
                int barHeightFull = heightPerLayer - 2 * margin;
                Color oc = inStarPower ? openColor.brighter() : openColor;
                g.setColor(oc);
                g.fillRect(barX, barY, barWidth, barHeightFull);

                // Draw dark blue border if in star power
                if (inStarPower) {
                    Color borderColor = new Color(0, 0, 139); // dark blue
                    java.awt.Stroke oldStroke = g.getStroke();
                    Color oldColor = g.getColor();
                    g.setColor(borderColor);
                    g.setStroke(new java.awt.BasicStroke(2));
                    g.drawRect(barX, barY, barWidth, barHeightFull);
                    g.setStroke(oldStroke);
                    g.setColor(oldColor);
                }

                // horizontal sustain tail if duration > 0
                if (note.duration > 0) {
                    int sustainEndX = margin + ((note.time + note.duration) % (width * timeScale)) / timeScale;
                    int tailY = barY + barHeightFull / 2 - 2;
                    int tailXStart = x + barWidth / 2;
                    int tailWidth = Math.max(1, sustainEndX - tailXStart);
                    g.fillRect(tailXStart, tailY, tailWidth, 4);
                }

                // forced/tap visual hints on open notes
                if (note.forced) {
                    Color oldColor = g.getColor();
                    java.awt.Stroke oldStroke = g.getStroke();
                    g.setColor(Color.BLACK);
                    g.setStroke(new java.awt.BasicStroke(2));
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
                if (lane < 0 || lane > 4) continue; // skip unknown lanes
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
                    java.awt.Stroke oldStroke = g.getStroke();
                    Color oldColor = g.getColor();
                    g.setColor(outline);
                    g.setStroke(new java.awt.BasicStroke(2));
                    g.draw(star);
                    g.setStroke(oldStroke);
                    g.setColor(oldColor);
                } else {
                    g.setColor(col);
                    g.fillOval(x, y, noteSize, noteSize);
                }

                // Draw sustain if duration > 0
                if (note.duration > 0) {
                    int sustainEndX = margin + ((note.time + note.duration) % (width * timeScale)) / timeScale;
                    int sustainWidth = sustainEndX - x;
                    if (sustainWidth > 0) {
                        Color oldColor = g.getColor();
                        g.setColor(col);
                        g.fillRect(x + noteSize / 2 - 2, y + noteSize / 2 - 2, sustainWidth, 4); // Draw sustain as a thin rectangle
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
        double angle = -Math.PI / 2; // start at top
        double step = Math.PI / points;
        for (int i = 0; i < points * 2; i++) {
            double r = (i % 2 == 0) ? outerRadius : innerRadius;
            double px = cx + Math.cos(angle) * r;
            double py = cy + Math.sin(angle) * r;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
            angle += step;
        }
        path.closePath();
        return path;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ChartParser <chart-file-path> <output-image-path>");
            return;
        }

        String filePath = args[0];
        String outputFilePath = args[1];

        try {
            ChartData chartData = parseChart(filePath);

            generateChartImage(chartData, outputFilePath);
            System.out.println("Chart image generated at: " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}