package ghopt.core.render;

import ghopt.core.model.ChartData;
import ghopt.core.model.Note;
import ghopt.core.model.StarPowerPhrase;
import ghopt.core.model.TimeSignatureEvent;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

public class ChartRenderer {

    private static final class TimingGridLine {
        private final int tick;
        private final boolean barLine;

        private TimingGridLine(int tick, boolean barLine) {
            this.tick = tick;
            this.barLine = barLine;
        }
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
        int laneSpacing = (heightPerLayer - 2 * margin) / 4;
        int timeScale = 2;

        int maxTime = chartData.getNotes().stream().mapToInt(Note::getTime).max().orElse(0);
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
            for (int i = 0; i < 5; i++) {
                int y = layerOffset + margin + i * laneSpacing;
                g.drawLine(margin, y, width - margin, y);
            }
        }

        List<TimingGridLine> timingGridLines = getTimingGridLines(chartData, maxTime);
        Stroke oldGridStroke = g.getStroke();
        for (TimingGridLine line : timingGridLines) {
            int tick = line.tick;
            int layer = tick / (width * timeScale);
            int layerOffset = layer * heightPerLayer;
            int x = margin + (tick % (width * timeScale)) / timeScale;
            int yStart = layerOffset + margin;
            int yEnd = layerOffset + heightPerLayer - margin;

            if (line.barLine) {
                g.setColor(new Color(90, 90, 90));
                g.setStroke(new BasicStroke(3f));
            } else {
                g.setColor(new Color(180, 180, 180));
                g.setStroke(new BasicStroke(1f));
            }

            g.drawLine(x, yStart, x, yEnd);
            if (line.barLine) {
                g.setColor(Color.DARK_GRAY);
                g.drawString(String.valueOf(tick), x, yStart - 10);
            }
        }
        g.setStroke(oldGridStroke);

        g.setColor(new Color(173, 216, 230, 128));
        for (StarPowerPhrase phrase : chartData.getStarPowerPhrases()) {
            int startLayer = (phrase.getStartTick() / (width * timeScale));
            int endLayer = (phrase.getEndTick() / (width * timeScale));

            for (int layer = startLayer; layer <= endLayer; layer++) {
                int layerOffset = layer * heightPerLayer;
                int xStart = margin + (layer == startLayer ? (phrase.getStartTick() % (width * timeScale)) / timeScale : 0);
                int xEnd = margin + (layer == endLayer ? (phrase.getEndTick() % (width * timeScale)) / timeScale : width);
                g.fillRect(xStart, layerOffset + margin, xEnd - xStart, heightPerLayer - 2 * margin);
            }
        }

        // Draw activation times highlighted in orange/gold
        if (activationTimes != null && !activationTimes.isEmpty()) {
            Set<Integer> activationSet = new HashSet<>(activationTimes);
            Color bandColor = new Color(255, 215, 0, 80);
            Color lineColor = new Color(255, 180, 0);
            Color labelBg = new Color(255, 200, 0);
            Color labelText = new Color(0, 0, 0);

            int bandHalfWidth = 12;
            Font labelFont = new Font("SansSerif", Font.BOLD, 22);

            for (int tick : activationSet) {
                int layer = tick / (width * timeScale);
                int layerOffset = layer * heightPerLayer;
                int layerStartTick = layer * width * timeScale;
                int x = margin + (tick - layerStartTick) / timeScale;

                int topY = layerOffset + margin - 20;
                int bottomY = layerOffset + heightPerLayer - margin + 20;
                int bandH = bottomY - topY;

                g.setColor(bandColor);
                g.fillRect(x - bandHalfWidth, topY, bandHalfWidth * 2, bandH);

                g.setColor(lineColor);
                g.setStroke(new BasicStroke(5));
                g.drawLine(x, topY, x, bottomY);

                g.setFont(labelFont);
                FontMetrics fm = g.getFontMetrics();
                String label = "ACTIVATE!";
                int labelW = fm.stringWidth(label) + 12;
                int labelH = fm.getHeight() + 4;
                int labelX = x - labelW / 2;
                int labelY = topY - labelH - 4;

                g.setColor(labelBg);
                g.fillRoundRect(labelX, labelY, labelW, labelH, 8, 8);

                g.setColor(new Color(180, 120, 0));
                g.setStroke(new BasicStroke(2));
                g.drawRoundRect(labelX, labelY, labelW, labelH, 8, 8);

                g.setColor(labelText);
                g.drawString(label, labelX + 6, labelY + fm.getAscent() + 2);
            }
        }

        // Draw notes with colors based on type across layers, including sustains
        Color[] noteColors = {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE, Color.ORANGE}; // types 0-4
        Color openColor = Color.MAGENTA; // open note color (type 7)
        for (Note note : chartData.getNotes()) {
            int layer = note.getTime() / (width * timeScale);
            int layerOffset = layer * heightPerLayer;
            int x = margin + (note.getTime() % (width * timeScale)) / timeScale;

            boolean inStarPower = chartData.getStarPowerPhrases().stream()
                .anyMatch(phrase -> phrase.containsTick(note.getTime()));

            if (note.isOpen()) {
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

                if (note.getDuration() > 0) {
                    int sustainStart = note.getTime();
                    int sustainEnd = note.getTime() + note.getDuration();
                    int sustainStartLayer = sustainStart / (width * timeScale);
                    int sustainEndLayer = sustainEnd / (width * timeScale);

                    for (int sustainLayer = sustainStartLayer; sustainLayer <= sustainEndLayer; sustainLayer++) {
                        int layerTickStart = sustainLayer * width * timeScale;
                        int layerTickEnd = (sustainLayer + 1) * width * timeScale;
                        int segmentStartTick = Math.max(sustainStart, layerTickStart);
                        int segmentEndTick = Math.min(sustainEnd, layerTickEnd);

                        if (segmentEndTick <= segmentStartTick) {
                            continue;
                        }

                        int segmentStartX = margin + (segmentStartTick - layerTickStart) / timeScale;
                        int segmentEndX = margin + (segmentEndTick - layerTickStart) / timeScale;
                        int segmentLayerOffset = sustainLayer * heightPerLayer;
                        int tailY = segmentLayerOffset + margin + (heightPerLayer - 2 * margin) / 2 - 2;
                        int tailWidth = Math.max(1, segmentEndX - segmentStartX);

                        g.fillRect(segmentStartX, tailY, tailWidth, 4);
                    }
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
            } else {
                int lane = note.getType();
                if (lane < 0 || lane > 4) continue;

                int y = layerOffset + margin + lane * laneSpacing - noteSize / 2;
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

                if (note.getDuration() > 0) {
                    int sustainStart = note.getTime();
                    int sustainEnd = note.getTime() + note.getDuration();
                    int sustainStartLayer = sustainStart / (width * timeScale);
                    int sustainEndLayer = sustainEnd / (width * timeScale);

                    Color oldColor = g.getColor();
                    g.setColor(col);
                    for (int sustainLayer = sustainStartLayer; sustainLayer <= sustainEndLayer; sustainLayer++) {
                        int layerTickStart = sustainLayer * width * timeScale;
                        int layerTickEnd = (sustainLayer + 1) * width * timeScale;
                        int segmentStartTick = Math.max(sustainStart, layerTickStart);
                        int segmentEndTick = Math.min(sustainEnd, layerTickEnd);

                        if (segmentEndTick <= segmentStartTick) {
                            continue;
                        }

                        int segmentStartX = margin + (segmentStartTick - layerTickStart) / timeScale;
                        int segmentEndX = margin + (segmentEndTick - layerTickStart) / timeScale;
                        int segmentLayerOffset = sustainLayer * heightPerLayer;
                        int segmentY = segmentLayerOffset + margin + lane * laneSpacing - 2;
                        int segmentWidth = Math.max(1, segmentEndX - segmentStartX);

                        g.fillRect(segmentStartX, segmentY, segmentWidth, 4);
                    }
                    g.setColor(oldColor);
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

    private static List<TimingGridLine> getTimingGridLines(ChartData chartData, int maxTick) {
        List<TimingGridLine> lines = new ArrayList<>();
        if (maxTick < 0) {
            return lines;
        }

        List<TimeSignatureEvent> timeSignatures = chartData.getTimeSignatureEvents();
        if (timeSignatures == null || timeSignatures.isEmpty()) {
            timeSignatures = new ArrayList<>();
            timeSignatures.add(new TimeSignatureEvent(0, 4, 4));
        }

        for (int i = 0; i < timeSignatures.size(); i++) {
            TimeSignatureEvent ts = timeSignatures.get(i);
            int segmentStart = Math.max(0, ts.getTick());
            int segmentEnd = (i + 1 < timeSignatures.size())
                    ? timeSignatures.get(i + 1).getTick()
                    : maxTick + 1;

            int denominator = ts.getDenominator() <= 0 ? 4 : ts.getDenominator();
            int numerator = ts.getNumerator() <= 0 ? 4 : ts.getNumerator();
            int ticksPerBeat = Math.max(1, (chartData.getResolution() * 4) / denominator);
            int ticksPerBar = Math.max(1, ticksPerBeat * numerator);

            for (int barTick = segmentStart; barTick < segmentEnd && barTick <= maxTick; barTick += ticksPerBar) {
                if (lines.isEmpty() || lines.get(lines.size() - 1).tick != barTick) {
                    lines.add(new TimingGridLine(barTick, true));
                }

                for (int beat = 1; beat < numerator; beat++) {
                    int beatTick = barTick + beat * ticksPerBeat;
                    if (beatTick >= segmentEnd || beatTick > maxTick) {
                        break;
                    }
                    lines.add(new TimingGridLine(beatTick, false));
                }
            }
        }

        if (lines.isEmpty() || lines.get(0).tick != 0) {
            lines.add(0, new TimingGridLine(0, true));
        }

        lines.sort((a, b) -> {
            if (a.tick != b.tick) {
                return Integer.compare(a.tick, b.tick);
            }
            if (a.barLine == b.barLine) {
                return 0;
            }
            return a.barLine ? -1 : 1;
        });

        List<TimingGridLine> deduped = new ArrayList<>();
        for (TimingGridLine line : lines) {
            if (deduped.isEmpty() || deduped.get(deduped.size() - 1).tick != line.tick) {
                deduped.add(line);
            }
        }

        return deduped;
    }
}
