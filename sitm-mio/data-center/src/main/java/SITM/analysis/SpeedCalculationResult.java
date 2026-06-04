package SITM.analysis;

import java.util.List;

public record SpeedCalculationResult(
        List<MonthlySpeedReport> reports,
        SpeedCalculationStats stats) {
}
