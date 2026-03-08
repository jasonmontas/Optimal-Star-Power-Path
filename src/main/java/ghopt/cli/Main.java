package ghopt.cli;
import ghopt.core.process.StarPowerOptimizer;
import ghopt.core.io.*;
import ghopt.core.model.ChartData;
import ghopt.core.render.ChartRenderer;
import java.io.File;

public class Main {
    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("Usage: java ghopt.cli.Main <input-file> <output-image>");
            return;
        }

        File input = new File(args[0]);
        String outputPath = args[1];

        // Check if input file exists
        if (!input.exists()) {
            System.out.println("Input file not found: " + input.getPath());
            return;
        }
        
        try {
            ChartSource source = ChooseChartSource.choose(input);
            ChartData data = source.parse(input);
            System.out.println("Notes: " + data.getNotes().size());
            System.out.println("Star Power: " + data.getStarPowerPhrases().size());
            StarPowerOptimizer.OptimalPath path =
            StarPowerOptimizer.findOptimalPath(data);
            ChartRenderer.render(data, outputPath);
            System.out.println("Chart image generated at: " + outputPath);
        
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}