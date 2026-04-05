package ghopt.core.io;

import java.io.File;

public class ChooseChartSource {

    public static ChartSource choose(File input) {
        String name = input.getName().toLowerCase();
        if (name.endsWith(".chart")) return new ChartParser();
        if (name.endsWith(".mid"))   return new MidiParser();
        throw new IllegalArgumentException("Unsupported file type: " + input.getName());
    }
}