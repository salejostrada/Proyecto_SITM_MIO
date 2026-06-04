package SITM;

import SITM.analysis.MonolithicSpeedCalculator;
import SITM.analysis.SpeedCalculationResult;
import SITM.analysis.SpeedCalculationStats;
import SITM.analysis.SpeedReportCsvWriter;

import com.zeroc.Ice.*;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            if ("monolithic".equals(mode) || "concurrent".equals(mode) || "distributed-coordinator".equals(mode)) {
                runAnalysis(args);
                return;
            }
            if ("distributed-worker".equals(mode)) {
                runWorker(args);
                return;
            }
        }

        try (Communicator communicator = Util.initialize(args)) {
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("ArchiveServiceAdapter", "default -p 10001");
            ArchiveServiceI servant = new ArchiveServiceI();
            adapter.add(servant, Util.stringToIdentity("ArchiveService"));
            adapter.activate();
            System.out.println("Data Center iniciado en el puerto 10001...");
            communicator.waitForShutdown();
        }
    }

    private static void runAnalysis(String[] args) {
        String mode = args[0].toLowerCase();
        if ("monolithic".equals(mode) && args.length < 4) {
            System.err.println("Uso: monolithic <datagramsFile> <routesFile> <outputFile>");
            System.exit(1);
        } else if ("concurrent".equals(mode) && args.length < 5) {
            System.err.println("Uso: concurrent <datagramsFile> <routesFile> <outputFile> <numThreads>");
            System.exit(1);
        } else if ("distributed-coordinator".equals(mode) && args.length < 5) {
            System.err.println("Uso: distributed-coordinator <datagramsFile> <routesFile> <outputFile> <worker1_host:port> [worker2_host:port ...]");
            System.exit(1);
        }

        Path datagramsFile = Path.of(args[1]);
        Path routesFile = Path.of(args[2]);
        Path outputFile = Path.of(args[3]);

        try {
            SITM.analysis.SpeedCalculator calculator;
            if ("concurrent".equals(mode)) {
                int numThreads = Integer.parseInt(args[4]);
                calculator = new SITM.analysis.ConcurrentSpeedCalculator(numThreads);
            } else if ("distributed-coordinator".equals(mode)) {
                try (Communicator communicator = Util.initialize()) {
                    java.util.List<SITM.SpeedWorkerPrx> workers = new java.util.ArrayList<>();
                    for (int i = 4; i < args.length; i++) {
                        String[] parts = args[i].split(":");
                        String host = parts[0];
                        String port = parts[1];
                        workers.add(SITM.SpeedWorkerPrx.checkedCast(
                            communicator.stringToProxy("SpeedWorker:default -h " + host + " -p " + port)));
                    }
                    calculator = new SITM.analysis.DistributedSpeedCalculator(workers);
                    executeAndReport(calculator, datagramsFile, routesFile, outputFile, mode);
                    return;
                }
            } else {
                calculator = new SITM.analysis.MonolithicSpeedCalculator();
            }

            executeAndReport(calculator, datagramsFile, routesFile, outputFile, mode);
        } catch (java.lang.Exception ex) {
            System.err.println("Error ejecutando calculo " + mode + ": " + ex.getMessage());
            ex.printStackTrace();
            System.exit(2);
        }
    }

    private static void executeAndReport(SITM.analysis.SpeedCalculator calculator, Path datagramsFile, Path routesFile, Path outputFile, String mode) throws java.io.IOException {
        SITM.analysis.SpeedCalculationResult result = calculator.calculate(datagramsFile, routesFile);
        new SITM.analysis.SpeedReportCsvWriter().write(outputFile, result.reports());
        printStats(mode, datagramsFile, routesFile, outputFile, result.stats());
    }

    private static final void runWorker(String[] args) {
        if (args.length < 2) {
            System.err.println("Uso: distributed-worker <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[1]);

        try (Communicator communicator = Util.initialize()) {
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("SpeedWorkerAdapter", "default -p " + port);
            DistributedWorkerI servant = new DistributedWorkerI();
            adapter.add(servant, Util.stringToIdentity("SpeedWorker"));
            adapter.activate();
            System.out.println("SpeedWorker iniciado en el puerto " + port + "...");
            communicator.waitForShutdown();
        } catch (java.lang.Exception ex) {
            System.err.println("Error iniciando worker: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(2);
        }
    }

    private static void printStats(
            String mode,
            Path datagramsFile,
            Path routesFile,
            Path outputFile,
            SpeedCalculationStats stats) {

        System.out.println("Mode: " + mode);
        System.out.println("Routes file: " + routesFile);
        System.out.println("Datagrams file: " + datagramsFile);
        System.out.println("Output file: " + outputFile);
        System.out.println("Active routes: " + stats.activeRoutes());
        System.out.println("Rows read: " + stats.rowsRead());
        System.out.println("Rows accepted: " + stats.rowsAccepted());
        System.out.println("Rows discarded: " + stats.rowsDiscarded());
        System.out.println("Rows discarded by invalid column count: " + stats.rowsWithInvalidColumnCount());
        System.out.println("Rows discarded by parse error: " + stats.rowsWithParseError());
        System.out.println("Rows discarded by inactive route: " + stats.rowsWithInactiveRoute());
        System.out.println("Rows discarded by invalid values: " + stats.rowsWithInvalidValues());
        System.out.println("Track groups: " + stats.trackGroups());
        System.out.println("Valid pairs: " + stats.validPairs());
        System.out.println("Discarded pairs: " + stats.discardedPairs());
        System.out.println("Pairs discarded by negative distance: " + stats.pairsWithNegativeDistance());
        System.out.println("Pairs discarded by invalid time: " + stats.pairsWithInvalidTime());
        System.out.println("Pairs discarded by unrealistic speed: " + stats.pairsWithUnrealisticSpeed());
        System.out.println("Out-of-order rows observed: " + stats.outOfOrderRows());
        System.out.println("Reports generated: " + stats.reportsGenerated());
        System.out.println("Elapsed ms: " + stats.elapsedMs());
    }
}
