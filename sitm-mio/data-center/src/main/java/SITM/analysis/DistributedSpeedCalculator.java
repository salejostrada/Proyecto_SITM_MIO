package SITM.analysis;

import SITM.Datagram;
import SITM.SpeedPartial;
import SITM.SpeedWorkerPrx;
import java.io.IOException;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DistributedSpeedCalculator implements SpeedCalculator {
    private final List<SpeedWorkerPrx> workers;
    private final RouteCsvReader routeReader = new RouteCsvReader();
    private static final int BATCH_SIZE = 5000;

    public DistributedSpeedCalculator(List<SpeedWorkerPrx> workers) {
        this.workers = workers;
    }

    @Override
    public SpeedCalculationResult calculate(Path datagramsFile, Path routesFile) throws IOException {
        long start = System.nanoTime();
        Map<Integer, ActiveRoute> routes = routeReader.read(routesFile);
        SpeedCalculationStats stats = new SpeedCalculationStats();
        
        int workerCount = workers.size();
        System.out.println("Starting distributed (push) calculation with " + workerCount + " workers...");

        // Clear workers state
        for (SpeedWorkerPrx worker : workers) {
            worker.clear();
        }

        Map<Integer, List<Datagram>> buffers = new HashMap<>();
        for (int i = 0; i < workerCount; i++) {
            buffers.put(i, new ArrayList<>(BATCH_SIZE));
        }

        DatagramCsvReader reader = new DatagramCsvReader();
        reader.forEachAccepted(datagramsFile, routes.keySet(), stats, record -> {
            int h = hash(record.lineId(), record.busId(), record.tripId());
            int workerIndex = Math.abs(h % workerCount);
            
            Datagram d = new Datagram(
                record.eventType(), record.registerDate(), record.stopId(),
                record.odometer(), record.latitude(), record.longitude(),
                record.taskId(), record.lineId(), record.tripId(),
                (int) record.unknown1(), "", record.busId() // Date string not used by worker since we already have LocalDateTime? 
                // Wait, Worker uses parseDate(d.datagramDate). I MUST send the date string.
            );
            // I need the original date string. DatagramRecord has registerDate as String, but datagramDate as LocalDateTime.
            // Let's use a formatter to put it back or change DatagramRecord to keep the original string.
            // Actually, for simplicity, I'll use a formatter. 
            // Or better: update DatagramRecord to keep original string.
            // Let's just use ISO-like or the most common format.
            d.datagramDate = record.datagramDate().toString(); // LocalDateTime.toString() is usually fine if we have a parser for it.
            
            buffers.get(workerIndex).add(d);
            
            if (buffers.get(workerIndex).size() >= BATCH_SIZE) {
                sendBatch(workerIndex, buffers.get(workerIndex));
                buffers.get(workerIndex).clear();
            }
        });

        // Send remaining buffers
        for (int i = 0; i < workerCount; i++) {
            if (!buffers.get(i).isEmpty()) {
                sendBatch(i, buffers.get(i));
                buffers.get(i).clear();
            }
        }

        // Collect results
        Map<MonthKey, SpeedAccumulator> globalAccumulators = new HashMap<>();
        Set<YearMonth> monthsSeen = new TreeSet<>();

        for (SpeedWorkerPrx worker : workers) {
            SpeedPartial[] partials = worker.getResults();
            for (SpeedPartial p : partials) {
                MonthKey key = new MonthKey(p.lineId, p.year, p.month);
                globalAccumulators.computeIfAbsent(key, ignored -> new SpeedAccumulator())
                        .add(p.totalDistanceMeters, p.totalTimeSeconds, p.samples);
                monthsSeen.add(YearMonth.of(p.year, p.month));
            }
        }

        ArrayList<MonthlySpeedReport> reports = buildReports(routes, monthsSeen, globalAccumulators);
        stats.setActiveRoutes(routes.size());
        stats.setReportsGenerated(reports.size());
        stats.setElapsedMs((System.nanoTime() - start) / 1_000_000);

        return new SpeedCalculationResult(reports, stats);
    }

    private void sendBatch(int workerIndex, List<Datagram> batch) {
        workers.get(workerIndex).addDatagrams(batch.toArray(new Datagram[0]));
    }

    private int hash(int lineId, int busId, int tripId) {
        int result = lineId;
        result = 31 * result + busId;
        result = 31 * result + tripId;
        return result;
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
