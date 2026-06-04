package SITM.analysis;

public record MonthKey(int lineId, int year, int month) implements Comparable<MonthKey> {
    public static MonthKey from(DatagramRecord record) {
        return new MonthKey(
                record.lineId(),
                record.datagramDate().getYear(),
                record.datagramDate().getMonthValue());
    }

    @Override
    public int compareTo(MonthKey other) {
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
