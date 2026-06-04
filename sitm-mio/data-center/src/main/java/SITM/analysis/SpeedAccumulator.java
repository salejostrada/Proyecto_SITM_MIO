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

    public void add(double distanceMeters, double timeSeconds, long samples) {
        this.totalDistanceMeters += distanceMeters;
        this.totalTimeSeconds += timeSeconds;
        this.samples += samples;
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

    public void merge(SpeedAccumulator other) {
        this.totalDistanceMeters += other.totalDistanceMeters;
        this.totalTimeSeconds += other.totalTimeSeconds;
        this.samples += other.samples;
    }
}
