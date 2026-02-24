package ghopt.cli;

import ghopt.core.io.*;
import ghopt.core.model.ChartData;
import java.io.File;

public class Main {
    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("Usage: java ghopt.cli.Main <input-file> <output-image>");
            return;
        }

        File input = new File(args[0]);
        String outputPath = args[1];

        ChartSource source;

        if (input.getName().endsWith(".chart")) {
            source = new ChartParser();
        } else if (input.getName().endsWith(".mid")) {
            source = new MidiParser();
        } else {
            throw new IllegalArgumentException("Unsupported file type.");
        }

        try {
            ChartData data = source.parse(input);
            ChartParser.generateChartImage(data, outputPath);
            System.out.println("Chart image generated at: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}