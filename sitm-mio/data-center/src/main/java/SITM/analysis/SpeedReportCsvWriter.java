package SITM.analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class SpeedReportCsvWriter {
    public void write(Path file, List<MonthlySpeedReport> reports) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("lineId,shortName,description,year,month,averageSpeedKmh,totalDistanceMeters,totalTimeSeconds,samples,status");
            writer.newLine();

            for (MonthlySpeedReport report : reports) {
                writer.write(toLine(report));
                writer.newLine();
            }
        }
    }

    private String toLine(MonthlySpeedReport report) {
        return report.lineId()
                + "," + CsvSupport.escape(report.shortName())
                + "," + CsvSupport.escape(report.description())
                + "," + report.year()
                + "," + report.month()
                + "," + format(report.averageSpeedKmh())
                + "," + format(report.totalDistanceMeters())
                + "," + format(report.totalTimeSeconds())
                + "," + report.samples()
                + "," + report.status();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
