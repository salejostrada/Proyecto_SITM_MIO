package SITM;

import SITM.analysis.ActiveRoute;
import SITM.analysis.DatagramRecord;
import SITM.analysis.MonthKey;
import SITM.analysis.RouteCsvReader;
import SITM.analysis.SpeedAccumulator;
import SITM.analysis.TrackKey;

import com.zeroc.Ice.Current;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DistributedWorkerI implements SpeedWorker {
    private final Map<TrackKey, DatagramRecord> previousByTrack = new HashMap<>();
    private final Map<MonthKey, SpeedAccumulator> accumulators = new HashMap<>();

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
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

    public DistributedWorkerI() {
        // No longer needs to load active routes locally.
    }

    @Override
    public synchronized void addDatagrams(Datagram[] batch, Current current) {
        for (Datagram d : batch) {
            LocalDateTime date;
            try {
                date = parseDate(d.datagramDate);
            } catch (DateTimeParseException e) {
                continue;
            }

            DatagramRecord record = new DatagramRecord(
                    d.eventType, d.registerDate, d.stopId, d.odometer,
                    d.latitude, d.longitude, d.taskId, d.lineId, d.tripId,
                    d.unknown1, date, d.busId);

            TrackKey trackKey = TrackKey.from(record);
            DatagramRecord previous = previousByTrack.get(trackKey);

            if (previous == null) {
                previousByTrack.put(trackKey, record);
                continue;
            }

            long deltaTimeSeconds = Duration.between(previous.datagramDate(), record.datagramDate()).getSeconds();
            if (deltaTimeSeconds > 0) {
                int deltaDistanceMeters = record.odometer() - previous.odometer();
                if (deltaDistanceMeters >= 0) {
                    double speedKmh = (deltaDistanceMeters / (double) deltaTimeSeconds) * 3.6;
                    if (speedKmh <= 100.0) {
                        MonthKey monthKey = MonthKey.from(record);
                        accumulators.computeIfAbsent(monthKey, ignored -> new SpeedAccumulator())
                                .add(deltaDistanceMeters, deltaTimeSeconds);
                    }
                }
            }
            previousByTrack.put(trackKey, record);
        }
    }

    @Override
    public synchronized SpeedPartial[] getResults(Current current) {
        List<SpeedPartial> partials = new ArrayList<>();
        accumulators.forEach((key, acc) -> {
            partials.add(new SpeedPartial(key.lineId(), key.year(), key.month(), 
                acc.totalDistanceMeters(), acc.totalTimeSeconds(), acc.samples()));
        });
        return partials.toArray(new SpeedPartial[0]);
    }

    @Override
    public synchronized void clear(Current current) {
        previousByTrack.clear();
        accumulators.clear();
        System.out.println("Worker state cleared.");
    }

    private LocalDateTime parseDate(String value) {
        String clean = value.trim().toUpperCase(Locale.ENGLISH);
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(clean, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new DateTimeParseException("Unsupported datagramDate format", value, 0);
    }
}
