package SITM.analysis;

public class SpeedAccumulator {
    private double totalDistanceMeters;
    private double totalTimeSeconds;
    private long samples;

    public void add(double distanceMeters, double timeSeconds) {
        totalDistanceMeters += distanceMeters;
        totalTimeSeconds += timeSeconds;
        samples++;
    }

    public double totalDistanceMeters() {
        return totalDistanceMeters;
    }

    public double totalTimeSeconds() {
        return totalTimeSeconds;
    }

    public long samples() {
        return samples;
    }

    public double averageSpeedKmh() {
        if (totalTimeSeconds <= 0) {
            return 0;
        }
        return (totalDistanceMeters / totalTimeSeconds) * 3.6;
    }
}
