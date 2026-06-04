package SITM.analysis;

public record MonthlySpeedReport(
        int lineId,
        String shortName,
        String description,
        int year,
        int month,
        double averageSpeedKmh,
        double totalDistanceMeters,
        double totalTimeSeconds,
        long samples,
        String status) implements Comparable<MonthlySpeedReport> {

    @Override
    public int compareTo(MonthlySpeedReport other) {
        int byYear = Integer.compare(year, other.year);
        if (byYear != 0) {
            return byYear;
        }
        int byMonth = Integer.compare(month, other.month);
        if (byMonth != 0) {
            return byMonth;
        }
        return Integer.compare(lineId, other.lineId);
    }
}
