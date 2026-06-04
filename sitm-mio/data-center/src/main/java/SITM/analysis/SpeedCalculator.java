package SITM.analysis;

import java.io.IOException;
import java.nio.file.Path;

public interface SpeedCalculator {
    SpeedCalculationResult calculate(Path datagramsFile, Path routesFile) throws IOException;
}
