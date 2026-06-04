package SITM.analysis;

public record TrackKey(int lineId, int busId, int tripId, int stopId) {
    public static TrackKey from(DatagramRecord record) {
        return new TrackKey(record.lineId(), record.busId(), record.tripId(), record.stopId());
    }
}
