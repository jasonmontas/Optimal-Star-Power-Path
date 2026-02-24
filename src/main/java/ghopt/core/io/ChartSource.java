package ghopt.core.io;

import ghopt.core.model.ChartData;
import java.io.File;

public interface ChartSource {
    ChartData parse(File file) throws Exception;
}