package ghopt.core.render;

import ghopt.core.model.ChartData;
import ghopt.core.model.Note;
import ghopt.core.model.StarPowerPhrase;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;


public class ChartRenderer {

    public static void render(ChartData chartData, String outputFilePath) throws IOException {
        render(chartData, java.util.Collections.emptyList(), outputFilePath);
    }

    public static void render(ChartData chartData, java.util.List<Integer> activationTimes, String outputFilePath) throws IOException {

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

        Color[] noteColors = {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE, Color.ORANGE};

        for (Note note : chartData.getNotes()) {

            int layer = note.getTime() / layerSpanTicks;
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

        // Draw activation markers as vertical yellow lines with a label
        g.setColor(new Color(255, 215, 0)); // gold
        g.setStroke(new BasicStroke(3));
        java.util.Set<Integer> activationSet = new java.util.HashSet<>(activationTimes);
        for (int tick : activationSet) {
            int layer = tick / layerSpanTicks;
            int layerOffset = layer * heightPerLayer;
            int layerStartTick = layer * layerSpanTicks;
            int x = margin + (tick - layerStartTick) / timeScale;
            g.drawLine(x, layerOffset + margin - 10, x, layerOffset + heightPerLayer - margin + 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString("SP", x + 4, layerOffset + margin - 2);
        }

        g.dispose();

        ImageIO.write(image, "png", new File(outputFilePath));
    }
}