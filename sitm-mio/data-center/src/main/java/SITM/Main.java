package SITM;

import SITM.analysis.MonolithicSpeedCalculator;
import SITM.analysis.SpeedCalculationResult;
import SITM.analysis.SpeedCalculationStats;
import SITM.analysis.SpeedReportCsvWriter;

import com.zeroc.Ice.*;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "monolithic".equalsIgnoreCase(args[0])) {
            runMonolithic(args);
            return;
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

    private static void runMonolithic(String[] args) {
        if (args.length < 4) {
            System.err.println("Uso: monolithic <datagramsFile> <routesFile> <outputFile>");
            System.err.println("Ejemplo: monolithic data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v1-mini.csv");
            System.exit(1);
        }

        Path datagramsFile = Path.of(args[1]);
        Path routesFile = Path.of(args[2]);
        Path outputFile = Path.of(args[3]);

        try {
            MonolithicSpeedCalculator calculator = new MonolithicSpeedCalculator();
            SpeedCalculationResult result = calculator.calculate(datagramsFile, routesFile);
            new SpeedReportCsvWriter().write(outputFile, result.reports());
            printStats(datagramsFile, routesFile, outputFile, result.stats());
        } catch (java.lang.Exception ex) {
            System.err.println("Error ejecutando calculo monolitico: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(2);
        }
    }

    private static void printStats(
            Path datagramsFile,
            Path routesFile,
            Path outputFile,
            SpeedCalculationStats stats) {

        System.out.println("Mode: monolithic");
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
