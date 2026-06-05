package SITM;

import com.zeroc.Ice.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main extends Application {

    private Communicator communicator;
    private WebEngine webEngine;
    private static String[] launchArgs;

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        webEngine = webView.getEngine();

        String mode = (launchArgs != null && launchArgs.length > 0) ? launchArgs[0].toLowerCase() : "monitoring";

        if ("dashboard".equals(mode)) {
            setupDashboard(stage, webView);
        } else {
            setupMonitoring(stage, webView);
        }

        stage.show();
    }

    private void setupDashboard(Stage stage, WebView webView) {
        if (launchArgs.length < 2) {
            System.err.println("Uso: dashboard <csvFile>");
            Platform.exit();
            return;
        }

        String csvPath = launchArgs[1];
        URL url = getClass().getResource("/dashboard.html");
        
        if (url != null) {
            webEngine.load(url.toExternalForm());
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    loadCsvData(csvPath);
                }
            });
        } else {
            System.err.println("No se encontró el archivo dashboard.html en resources");
        }

        stage.setTitle("SITM-MIO - Dashboard de Análisis");
        stage.setScene(new Scene(webView, 1200, 800));
    }

    private void loadCsvData(String csvPath) {
        try {
            Path path = Path.of(csvPath);
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return;

            String[] headers = lines.get(0).split(",");
            List<String> jsonParts = new ArrayList<>();

            for (int i = 1; i < lines.size(); i++) {
                List<String> values = parseCsvLine(lines.get(i));
                if (values.size() < headers.length) continue;

                StringBuilder obj = new StringBuilder("{");
                for (int j = 0; j < headers.length; j++) {
                    String val = values.get(j).replace("\"", "\\\"");
                    obj.append("\"").append(headers[j]).append("\":\"").append(val).append("\"");
                    if (j < headers.length - 1) obj.append(",");
                }
                obj.append("}");
                jsonParts.add(obj.toString());
            }

            String fullJson = "[" + String.join(",", jsonParts) + "]";
            String filename = path.getFileName().toString();
            
            // Escapar el JSON para ser pasado como string literal en JS
            String script = String.format("setData('%s', '%s')", fullJson.replace("'", "\\'"), filename);
            webEngine.executeScript(script);

        } catch (java.lang.Exception e) {
            System.err.println("Error cargando CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private void setupMonitoring(Stage stage, WebView webView) {
        URL url = getClass().getResource("/map.html");
        if (url != null) {
            webEngine.load(url.toExternalForm());
        } else {
            System.err.println("No se encontró el archivo map.html en resources");
        }

        stage.setTitle("SITM-MIO - Monitoreo en Tiempo Real");
        stage.setScene(new Scene(webView, 1024, 768));

        new Thread(this::initIce).start();
    }

    private void initIce() {
        try {
            communicator = Util.initialize(new String[]{});
            ObjectPrx base = communicator.stringToProxy("DatagramReceiver:default -p 10000");
            DatagramReceiverPrx receiver = DatagramReceiverPrx.checkedCast(base);

            if (receiver == null) {
                System.out.println("No se pudo conectar con el Event Processor (modo offline).");
                return;
            }

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("VisualizerCallbackAdapter", "default");
            
            MonitoringSubscriberI servant = new MonitoringSubscriberI(update -> {
                Platform.runLater(() -> {
                    String script = String.format(java.util.Locale.US, "updateBus(%d, %f, %f, %d, '%s')",
                            update.busId, update.pos.latitude, update.pos.longitude, update.lineId, update.timestamp);
                    webEngine.executeScript(script);
                });
            });

            ObjectPrx proxy = adapter.add(servant, new Identity("VisualizerCallback", ""));
            adapter.activate();

            MonitoringSubscriberPrx subPrx = MonitoringSubscriberPrx.uncheckedCast(proxy);
            receiver.subscribe(subPrx);
            
            System.out.println("Conectado y suscrito al Event Processor.");
            
            communicator.waitForShutdown();
        } catch (java.lang.Exception e) {
            System.err.println("Error en la comunicación Ice: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (communicator != null) {
            communicator.destroy();
        }
    }

    public static void main(String[] args) {
        launchArgs = args;
        launch(args);
    }
}
