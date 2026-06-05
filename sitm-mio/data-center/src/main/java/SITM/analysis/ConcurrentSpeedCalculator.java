package SITM.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConcurrentSpeedCalculator implements SpeedCalculator {
    private static final double MAX_REASONABLE_SPEED_KMH = 100.0;
    private static final long MAX_PAIR_TIME_GAP_SECONDS = 15 * 60;
    private static final int QUEUE_CAPACITY = 50000;
    private static final int LOG_INTERVAL = 1000000;

    private final RouteCsvReader routeReader = new RouteCsvReader();
    private final DatagramCsvReader datagramReader = new DatagramCsvReader();
    private final int numThreads;

    public ConcurrentSpeedCalculator(int numThreads) {
        this.numThreads = numThreads;
    }

    @Override
    public SpeedCalculationResult calculate(Path datagramsFile, Path routesFile) throws IOException {
        long start = System.nanoTime();

        Map<Integer, ActiveRoute> routes = routeReader.read(routesFile);
        SpeedCalculationStats globalStats = new SpeedCalculationStats();
        globalStats.setActiveRoutes(routes.size());

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Worker[] workers = new Worker[numThreads];
        for (int i = 0; i < numThreads; i++) {
            workers[i] = new Worker();
            executor.submit(workers[i]);
        }

        System.out.println("Processing datagrams with " + numThreads + " threads...");
        
        datagramReader.forEachAccepted(datagramsFile, routes.keySet(), globalStats, record -> {
            // Partition by hash of primitive values to avoid TrackKey allocation in main thread
            int h = hash(record.lineId(), record.busId(), record.tripId());
            int partition = Math.abs(h % numThreads);
            
            try {
                workers[partition].queue.put(record);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Main thread interrupted while feeding workers", e);
            }

            if (globalStats.rowsAccepted() % LOG_INTERVAL == 0) {
                System.out.println("Rows accepted: " + globalStats.rowsAccepted() + "...");
            }
        });

        System.out.println("Finished reading file. Draining queues...");
        for (Worker worker : workers) {
            worker.stop();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("Merging results...");
        // Merge results ... (rest of logic same)
        Map<MonthKey, SpeedAccumulator> globalAccumulators = new HashMap<>();
        Set<YearMonth> monthsSeen = new TreeSet<>();
        int totalTrackGroups = 0;

        for (Worker worker : workers) {
            globalStats.merge(worker.stats);
            totalTrackGroups += worker.previousByTrack.size();
            
            worker.accumulators.forEach((key, acc) -> {
                globalAccumulators.computeIfAbsent(key, ignored -> new SpeedAccumulator())
                        .merge(acc);
            });
            
            worker.monthsSeen.forEach(month -> monthsSeen.add(month));
        }

        ArrayList<MonthlySpeedReport> reports = buildReports(routes, monthsSeen, globalAccumulators);
        globalStats.setTrackGroups(totalTrackGroups);
        globalStats.setReportsGenerated(reports.size());
        globalStats.setElapsedMs((System.nanoTime() - start) / 1_000_000);

        return new SpeedCalculationResult(reports, globalStats);
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

    private int hash(int lineId, int busId, int tripId) {
        int result = lineId;
        result = 31 * result + busId;
        result = 31 * result + tripId;
        return result;
    }

    private static class Worker implements Runnable {
        final BlockingQueue<DatagramRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        final Map<TrackKey, DatagramRecord> previousByTrack = new HashMap<>();
        final Map<MonthKey, SpeedAccumulator> accumulators = new HashMap<>();
        final Set<YearMonth> monthsSeen = new TreeSet<>();
        final SpeedCalculationStats stats = new SpeedCalculationStats();
        private volatile boolean running = true;
        private static final DatagramRecord POISON_PILL = new DatagramRecord(0, "", 0, 0, 0, 0, 0, 0, 0, 0, null, 0);

        public void stop() {
            try {
                queue.put(POISON_PILL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void run() {
            try {
                while (running) {
                    DatagramRecord record = queue.take();
                    if (record == POISON_PILL) {
                        running = false;
                        break;
                    }
                    process(record);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void process(DatagramRecord record) {
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
        }
    }
}
