package SITM.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MonolithicSpeedCalculator implements SpeedCalculator {
    private static final double MAX_REASONABLE_SPEED_KMH = 100.0;
    private static final long MAX_PAIR_TIME_GAP_SECONDS = 15 * 60;

    private final RouteCsvReader routeReader = new RouteCsvReader();
    private final DatagramCsvReader datagramReader = new DatagramCsvReader();

    @Override
    public SpeedCalculationResult calculate(Path datagramsFile, Path routesFile) throws IOException {
        long start = System.nanoTime();

        Map<Integer, ActiveRoute> routes = routeReader.read(routesFile);
        SpeedCalculationStats stats = new SpeedCalculationStats();
        stats.setActiveRoutes(routes.size());

        Map<TrackKey, DatagramRecord> previousByTrack = new HashMap<>();
        Map<MonthKey, SpeedAccumulator> accumulators = new HashMap<>();
        Set<YearMonth> monthsSeen = new TreeSet<>();
        LocalDateTime[] lastGlobalDate = new LocalDateTime[1];

        datagramReader.forEachAccepted(datagramsFile, routes.keySet(), stats, record -> {
            if (lastGlobalDate[0] != null && record.datagramDate().isBefore(lastGlobalDate[0])) {
                stats.incrementOutOfOrderRows();
            }
            lastGlobalDate[0] = record.datagramDate();

            monthsSeen.add(YearMonth.from(record.datagramDate()));
            TrackKey trackKey = TrackKey.from(record);
            DatagramRecord previous = previousByTrack.get(trackKey);

            if (previous == null) {
                previousByTrack.put(trackKey, record);
                return;
            }

            long deltaTimeSeconds = Duration.between(previous.datagramDate(), record.datagramDate()).getSeconds();
            if (deltaTimeSeconds <= 0) {
                stats.incrementInvalidTimePair();
                return;
            }
            if (deltaTimeSeconds > MAX_PAIR_TIME_GAP_SECONDS) {
                stats.incrementExcessiveTimeGapPair();
                previousByTrack.put(trackKey, record);
                return;
            }

            int deltaDistanceMeters = record.odometer() - previous.odometer();
            if (deltaDistanceMeters < 0) {
                stats.incrementNegativeDistancePair();
                previousByTrack.put(trackKey, record);
                return;
            }

            double speedKmh = (deltaDistanceMeters / (double) deltaTimeSeconds) * 3.6;
            if (speedKmh > MAX_REASONABLE_SPEED_KMH) {
                stats.incrementUnrealisticSpeedPair();
                previousByTrack.put(trackKey, record);
                return;
            }

            MonthKey monthKey = MonthKey.from(record);
            accumulators.computeIfAbsent(monthKey, ignored -> new SpeedAccumulator())
                    .add(deltaDistanceMeters, deltaTimeSeconds);
            stats.incrementValidPairs();
            previousByTrack.put(trackKey, record);
        });

        ArrayList<MonthlySpeedReport> reports = buildReports(routes, monthsSeen, accumulators);
        stats.setTrackGroups(previousByTrack.size());
        stats.setReportsGenerated(reports.size());
        stats.setElapsedMs((System.nanoTime() - start) / 1_000_000);
        return new SpeedCalculationResult(reports, stats);
    }

    private ArrayList<MonthlySpeedReport> buildReports(
            Map<Integer, ActiveRoute> routes,
            Set<YearMonth> monthsSeen,
            Map<MonthKey, SpeedAccumulator> accumulators) {

        ArrayList<MonthlySpeedReport> reports = new ArrayList<>();
        for (YearMonth month : monthsSeen) {
            for (ActiveRoute route : routes.values()) {
                MonthKey key = new MonthKey(route.lineId(), month.getYear(), month.getMonthValue());
                SpeedAccumulator accumulator = accumulators.get(key);
                if (accumulator == null || accumulator.samples() == 0) {
                    reports.add(new MonthlySpeedReport(
                            route.lineId(),
                            route.shortName(),
                            route.description(),
                            month.getYear(),
                            month.getMonthValue(),
                            0,
                            0,
                            0,
                            0,
                            "NO_DATA"));
                } else {
                    reports.add(new MonthlySpeedReport(
                            route.lineId(),
                            route.shortName(),
                            route.description(),
                            month.getYear(),
                            month.getMonthValue(),
                            accumulator.averageSpeedKmh(),
                            accumulator.totalDistanceMeters(),
                            accumulator.totalTimeSeconds(),
                            accumulator.samples(),
                            "OK"));
                }
            }
        }
        reports.sort(null);
        return reports;
    }
}
