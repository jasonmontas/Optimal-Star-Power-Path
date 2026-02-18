package ghopt.core.io;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import java.awt.geom.GeneralPath;

import ghopt.core.model.ChartData;
import ghopt.core.model.Note;
import ghopt.core.model.StarPowerPhrase;

public class ChartParser {

    public static ChartData parseChart(String filePath) throws IOException {
        ChartData chartData = new ChartData();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
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
        chartData.getNotes().sort(Comparator.comparingInt(Note::getTime));
        chartData.getStarPowerPhrases().sort(Comparator.comparingInt(StarPowerPhrase::getStartTick));

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

    public static void generateChartImage(ChartData chartData, String outputFilePath) throws IOException {
        int width = 4000;
        int heightPerLayer = 600;
        int margin = 50;
        int noteSize = 20;
        int laneHeight = (heightPerLayer - 2 * margin) / 5;
        int timeScale = 2;

        int maxTime = chartData.maxTick();
        int layerSpanTicks = width * timeScale;
        int totalLayers = (maxTime / layerSpanTicks) + 1;
        int totalHeight = totalLayers * heightPerLayer;

        BufferedImage image = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, totalHeight);

        // Lanes
        g.setColor(Color.LIGHT_GRAY);
        for (int layer = 0; layer < totalLayers; layer++) {
            int layerOffset = layer * heightPerLayer;
            for (int i = 0; i <= 5; i++) {
                int y = layerOffset + margin + i * laneHeight;
                g.drawLine(margin, y, width - margin, y);
            }
        }

        // Time markers
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

        // Star power phrases across layers
        g.setColor(new Color(173, 216, 230, 128));
        for (StarPowerPhrase phrase : chartData.getStarPowerPhrases()) {
            int startLayer = phrase.getStartTick() / layerSpanTicks;
            int endLayer = phrase.getEndTick() / layerSpanTicks;

            for (int layer = startLayer; layer <= endLayer; layer++) {
                int layerOffset = layer * heightPerLayer;
                int layerStartTick = layer * layerSpanTicks;
                int layerEndTick = layerStartTick + layerSpanTicks;

                int segStartTick = Math.max(phrase.getStartTick(), layerStartTick);
                int segEndTick = Math.min(phrase.getEndTick(), layerEndTick);

                int xStart = margin + (segStartTick - layerStartTick) / timeScale;
                int xEnd = margin + (segEndTick - layerStartTick) / timeScale;

                g.fillRect(xStart, layerOffset + margin, xEnd - xStart, heightPerLayer - 2 * margin);
            }
        }

        // Prep for fast in-star-power checks
        List<StarPowerPhrase> phrases = chartData.getStarPowerPhrases();
        int spIndex = 0;

        Color[] noteColors = {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE, Color.ORANGE};
        Color openColor = Color.MAGENTA;

        // Notes (assumes chartData.notes sorted by time)
        for (Note note : chartData.getNotes()) {
            int t = note.getTime();

            while (spIndex < phrases.size() && phrases.get(spIndex).getEndTick() <= t) {
                spIndex++;
            }
            boolean inStarPower = false;
            if (spIndex < phrases.size()) {
                StarPowerPhrase p = phrases.get(spIndex);
                inStarPower = p.containsTick(t);
            }

            if (note.isOpen()) {
                drawOpenNote(g, note, inStarPower, openColor, noteSize, margin, laneHeight,
                        heightPerLayer, layerSpanTicks, timeScale);
            } else {
                int lane = note.getType();
                if (lane < 0 || lane > 4) {
                    continue;
                }
                drawLaneNote(g, note, lane, inStarPower, noteColors, noteSize, margin, laneHeight,
                        heightPerLayer, layerSpanTicks, timeScale);
            }
        }

        g.dispose();
        ImageIO.write(image, "png", new File(outputFilePath));
    }

    private static void drawOpenNote(Graphics2D g,
                                    Note note,
                                    boolean inStarPower,
                                    Color openColor,
                                    int noteSize,
                                    int margin,
                                    int laneHeight,
                                    int heightPerLayer,
                                    int layerSpanTicks,
                                    int timeScale) {
        int layer = note.getTime() / layerSpanTicks;
        int layerOffset = layer * heightPerLayer;
        int layerStartTick = layer * layerSpanTicks;

        int x = margin + (note.getTime() - layerStartTick) / timeScale;

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

        // Sustain tail across layers
        if (note.getDuration() > 0) {
            drawSustainAcrossLayers(g,
                    note.getTime(),
                    note.getTime() + note.getDuration(),
                    barY + barHeightFull / 2 - 2,
                    4,
                    oc,
                    margin,
                    heightPerLayer,
                    layerSpanTicks,
                    timeScale,
                    x + barWidth / 2);
        }

        if (note.isForced()) {
            Color oldColor = g.getColor();
            Stroke oldStroke = g.getStroke();
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(2));
            g.drawRect(barX, barY, barWidth, barHeightFull);
            g.setStroke(oldStroke);
            g.setColor(oldColor);
        }

        if (note.isTap()) {
            int tickY = barY + barHeightFull / 2;
            g.setColor(Color.WHITE);
            g.fillRect(x - 2, tickY - 2, 4, 4);
        }
    }

    private static void drawLaneNote(Graphics2D g,
                                    Note note,
                                    int lane,
                                    boolean inStarPower,
                                    Color[] noteColors,
                                    int noteSize,
                                    int margin,
                                    int laneHeight,
                                    int heightPerLayer,
                                    int layerSpanTicks,
                                    int timeScale) {
        int layer = note.getTime() / layerSpanTicks;
        int layerOffset = layer * heightPerLayer;
        int layerStartTick = layer * layerSpanTicks;

        int x = margin + (note.getTime() - layerStartTick) / timeScale;
        int y = layerOffset + margin + lane * laneHeight + laneHeight / 2 - noteSize / 2;

        Color base = noteColors[lane];
        Color col = inStarPower ? base.brighter() : base;

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

        // Sustain across layers (thin horizontal bar)
        if (note.getDuration() > 0) {
            int sustainY = y + noteSize / 2 - 2;
            int startX = x + noteSize / 2 - 2;
            drawSustainAcrossLayers(g,
                    note.getTime(),
                    note.getTime() + note.getDuration(),
                    sustainY,
                    4,
                    col,
                    margin,
                    heightPerLayer,
                    layerSpanTicks,
                    timeScale,
                    startX);
        }
    }

    private static void drawSustainAcrossLayers(Graphics2D g,
                                               int startTick,
                                               int endTick,
                                               int localY,
                                               int h,
                                               Color color,
                                               int margin,
                                               int heightPerLayer,
                                               int layerSpanTicks,
                                               int timeScale,
                                               int startXOverride) {
        if (endTick <= startTick) {
            return;
        }

        int startLayer = startTick / layerSpanTicks;
        int endLayer = endTick / layerSpanTicks;

        g.setColor(color);

        for (int layer = startLayer; layer <= endLayer; layer++) {
            int layerStartTick = layer * layerSpanTicks;
            int layerEndTick = layerStartTick + layerSpanTicks;
            int layerOffset = layer * heightPerLayer;

            int segStartTick = Math.max(startTick, layerStartTick);
            int segEndTick = Math.min(endTick, layerEndTick);

            int xStart;
            if (layer == startLayer) {
                xStart = startXOverride;
            } else {
                xStart = margin; // start of drawable region for wrapped layer
            }

            int xEnd = margin + (segEndTick - layerStartTick) / timeScale;
            int w = xEnd - xStart;
            if (w > 0) {
                g.fillRect(xStart, layerOffset + localY, w, h);
            }
        }
    }

    private static Shape createStar(int cx, int cy, int outerRadius, int innerRadius, int points) {
        GeneralPath path = new GeneralPath();
        double angle = -Math.PI / 2;
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
}
