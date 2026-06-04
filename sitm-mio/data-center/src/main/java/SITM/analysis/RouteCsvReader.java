package SITM.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RouteCsvReader {
    public Map<Integer, ActiveRoute> read(Path file) throws IOException {
        Map<Integer, ActiveRoute> routes = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = CsvSupport.parseLine(line);
                if (lineNumber == 1 && isHeader(fields)) {
                    continue;
                }
                if (fields.size() < 7) {
                    continue;
                }

                int lineId = Integer.parseInt(fields.get(0));
                int planVersionId = Integer.parseInt(fields.get(1));
                ActiveRoute route = new ActiveRoute(
                        lineId,
                        planVersionId,
                        fields.get(2),
                        fields.get(3),
                        fields.get(5),
                        fields.get(6));
                routes.put(lineId, route);
            }
        }

        return routes;
    }

    private boolean isHeader(List<String> fields) {
        return !fields.isEmpty() && "LINEID".equalsIgnoreCase(fields.get(0).replace("\"", ""));
    }
}
