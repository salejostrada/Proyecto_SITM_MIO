package SITM.analysis;

import java.time.LocalDateTime;

public record DatagramRecord(
        int eventType,
        String registerDate,
        int stopId,
        int odometer,
        int latitude,
        int longitude,
        int taskId,
        int lineId,
        int tripId,
        long unknown1,
        LocalDateTime datagramDate,
        int busId) {
}
