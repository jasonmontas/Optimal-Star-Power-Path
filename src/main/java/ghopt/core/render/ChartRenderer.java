package ghopt.core.render;

import ghopt.core.model.ChartData;
import ghopt.core.model.Note;
import ghopt.core.model.StarPowerPhrase;
import ghopt.core.model.TimeSignatureEvent;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

    public static void render(ChartData chartData, String outputFilePath) throws IOException {
        render(chartData, Collections.emptyList(), outputFilePath);
    }

    public static void render(ChartData chartData, List<Integer> activationTimes, String outputFilePath) throws IOException {

        int width        = 4000;
        int heightPerLayer = 600;
        int margin       = 50;
        int noteSize     = 20;
        int laneHeight   = (heightPerLayer - 2 * margin) / 5;
        int timeScale    = 2;

        int maxTime        = chartData.maxTick();
        int layerSpanTicks = width * timeScale;
        int totalLayers    = (maxTime / layerSpanTicks) + 1;
        int totalHeight    = totalLayers * heightPerLayer;

        BufferedImage image = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, totalHeight);

        // Lane lines
        g.setColor(Color.LIGHT_GRAY);
        for (int layer = 0; layer < totalLayers; layer++) {
            int layerOffset = layer * heightPerLayer;
            for (int i = 0; i <= 5; i++) {
                int y = layerOffset + margin + i * laneHeight;
                g.drawLine(margin, y, width - margin, y);
            }
        }

        // Beat/bar timing grid lines
        List<TimingGridLine> timingGridLines = getTimingGridLines(chartData, maxTime);
        Stroke oldGridStroke = g.getStroke();
        for (TimingGridLine line : timingGridLines) {
            int tick = line.tick;
            int layer = tick / layerSpanTicks;
            int layerOffset = layer * heightPerLayer;
            int x = margin + (tick % layerSpanTicks) / timeScale;
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

        // Dark separator line between rows
        g.setColor(new Color(80, 80, 80));
        g.setStroke(new BasicStroke(3));
        for (int layer = 0; layer < totalLayers; layer++) {
            int y = layer * heightPerLayer + margin;
            g.drawLine(0, y, width, y);
        }

        // SP phrase highlight (light blue)
        g.setColor(new Color(173, 216, 230, 128));
        for (StarPowerPhrase phrase : chartData.getStarPowerPhrases()) {
            int startLayer = phrase.getStartTick() / layerSpanTicks;
            int endLayer   = phrase.getEndTick()   / layerSpanTicks;
            for (int layer = startLayer; layer <= endLayer; layer++) {
                int layerOffset    = layer * heightPerLayer;
                int layerStartTick = layer * layerSpanTicks;
                int layerEndTick   = layerStartTick + layerSpanTicks;
                int segStartTick   = Math.max(phrase.getStartTick(), layerStartTick);
                int segEndTick     = Math.min(phrase.getEndTick(),   layerEndTick);
                int xStart = margin + (segStartTick - layerStartTick) / timeScale;
                int xEnd   = margin + (segEndTick   - layerStartTick) / timeScale;
                g.fillRect(xStart, layerOffset + margin, xEnd - xStart, heightPerLayer - 2 * margin);
            }
        }

        // Notes
        Color[] noteColors = {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE, Color.ORANGE};
        for (Note note : chartData.getNotes()) {
            int layer       = note.getTime() / layerSpanTicks;
            int layerOffset = layer * heightPerLayer;
            int layerStartTick = layer * layerSpanTicks;
            int x = margin + (note.getTime() - layerStartTick) / timeScale;
            if (note.isOpen()) {
                g.setColor(Color.MAGENTA);
                g.fillRect(x - 3, layerOffset + margin, 6, heightPerLayer - 2 * margin);
            } else {
                int lane = note.getType();
                if (lane < 0 || lane > 4) continue;
                int y = layerOffset + margin + lane * laneHeight + laneHeight / 2 - noteSize / 2;
                g.setColor(noteColors[lane]);
                g.fillOval(x, y, noteSize, noteSize);
            }
        }

        // ── SP activation markers ─────────────────────────────────────────
        // Drawn last so they appear on top of everything else.
        // Each marker is:
        //   - A bright gold semi-transparent filled band across the full lane area
        //   - A thick solid gold center line
        //   - A bold "ACTIVATE!" label above the band

        Set<Integer> activationSet = new HashSet<>(activationTimes);
        Color bandColor   = new Color(255, 215, 0, 80);   // transparent gold fill
        Color lineColor   = new Color(255, 180, 0);        // solid bright gold line
        Color labelBg     = new Color(255, 200, 0);        // label background
        Color labelText   = new Color(0, 0, 0);            // black text on label

        int bandHalfWidth = 12; // pixels either side of center line
        Font labelFont    = new Font("SansSerif", Font.BOLD, 22);

        for (int tick : activationSet) {
            int layer          = tick / layerSpanTicks;
            int layerOffset    = layer * heightPerLayer;
            int layerStartTick = layer * layerSpanTicks;
            int x = margin + (tick - layerStartTick) / timeScale;

            int topY    = layerOffset + margin - 20;
            int bottomY = layerOffset + heightPerLayer - margin + 20;
            int bandH   = bottomY - topY;

            // Filled gold band
            g.setColor(bandColor);
            g.fillRect(x - bandHalfWidth, topY, bandHalfWidth * 2, bandH);

            // Thick center line
            g.setColor(lineColor);
            g.setStroke(new BasicStroke(5));
            g.drawLine(x, topY, x, bottomY);

            // Label background pill
            g.setFont(labelFont);
            FontMetrics fm  = g.getFontMetrics();
            String label    = "ACTIVATE!";
            int labelW      = fm.stringWidth(label) + 12;
            int labelH      = fm.getHeight() + 4;
            int labelX      = x - labelW / 2;
            int labelY      = topY - labelH - 4;

            g.setColor(labelBg);
            g.fillRoundRect(labelX, labelY, labelW, labelH, 8, 8);

            g.setColor(new Color(180, 120, 0)); // dark gold border
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(labelX, labelY, labelW, labelH, 8, 8);

            g.setColor(labelText);
            g.drawString(label, labelX + 6, labelY + fm.getAscent() + 2);
        }

        g.dispose();
        ImageIO.write(image, "png", new File(outputFilePath));
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