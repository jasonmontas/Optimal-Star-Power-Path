package ghopt.cli;

import ghopt.core.io.ChartParser;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ghopt.cli.Main <chart-file-path> <output-image-path>");
            System.out.println("Example: java ghopt.cli.Main resources/Song/notes.chart output.png");
            return;
        }

        String chartPath = args[0];
        String outputPath = args[1];

        try {
            ChartParser.ChartData chartData = ChartParser.parseChart(chartPath);
            ChartParser.generateChartImage(chartData, outputPath);
            System.out.println("Chart image generated at: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}