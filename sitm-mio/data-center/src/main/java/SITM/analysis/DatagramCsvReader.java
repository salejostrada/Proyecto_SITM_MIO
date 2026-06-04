package SITM.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class DatagramCsvReader {
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S", Locale.ENGLISH),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("dd-MMM-yy hh.mm.ss")
                    .optionalStart()
                    .appendFraction(ChronoField.MICRO_OF_SECOND, 1, 6, true)
                    .optionalEnd()
                    .appendPattern(" a")
                    .toFormatter(Locale.ENGLISH));

    public void forEachAccepted(
            Path file,
            Set<Integer> activeLineIds,
            SpeedCalculationStats stats,
            Consumer<DatagramRecord> consumer) throws IOException {

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                stats.incrementRowsRead();

                List<String> fields = CsvSupport.parseLine(line);
                if (fields.size() < 12) {
                    stats.incrementInvalidColumnCount();
                    continue;
                }

                DatagramRecord record;
                try {
                    record = parse(fields);
                } catch (RuntimeException ex) {
                    stats.incrementParseError();
                    continue;
                }

                if (!activeLineIds.contains(record.lineId())) {
                    stats.incrementInactiveRoute();
                    continue;
                }
                if (!hasValidRequiredValues(record)) {
                    stats.incrementInvalidValues();
                    continue;
                }

                stats.incrementRowsAccepted();
                consumer.accept(record);
            }
        }
    }

    private DatagramRecord parse(List<String> fields) {
        return new DatagramRecord(
                parseInt(fields.get(0)),
                fields.get(1),
                parseInt(fields.get(2)),
                parseInt(fields.get(3)),
                parseInt(fields.get(4)),
                parseInt(fields.get(5)),
                parseInt(fields.get(6)),
                parseInt(fields.get(7)),
                parseInt(fields.get(8)),
                parseLongFlexible(fields.get(9)),
                parseDate(fields.get(10)),
                parseInt(fields.get(11)));
    }

    private boolean hasValidRequiredValues(DatagramRecord record) {
        return record.busId() > 0
                && record.tripId() >= 0
                && record.stopId() >= 0
                && record.odometer() >= 0
                && record.latitude() != -1
                && record.longitude() != -1;
    }

    private int parseInt(String value) {
        return (int) parseLongFlexible(value);
    }

    private long parseLongFlexible(String value) {
        String clean = value.trim();
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException ex) {
            return (long) Double.parseDouble(clean);
        }
    }

    private LocalDateTime parseDate(String value) {
        String clean = value.trim().toUpperCase(Locale.ENGLISH);
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(clean, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next supported format.
            }
        }
        throw new DateTimeParseException("Unsupported datagramDate format", value, 0);
    }
}
