package SITM.analysis;

public class SpeedCalculationStats {
    private long rowsRead;
    private long rowsAccepted;
    private long rowsDiscarded;
    private long rowsWithInvalidColumnCount;
    private long rowsWithParseError;
    private long rowsWithInactiveRoute;
    private long rowsWithInvalidValues;
    private long validPairs;
    private long discardedPairs;
    private long pairsWithNegativeDistance;
    private long pairsWithInvalidTime;
    private long pairsWithUnrealisticSpeed;
    private long outOfOrderRows;
    private int activeRoutes;
    private int trackGroups;
    private int reportsGenerated;
    private long elapsedMs;

    public void incrementRowsRead() {
        rowsRead++;
    }

    public void incrementRowsAccepted() {
        rowsAccepted++;
    }

    public void incrementInvalidColumnCount() {
        rowsDiscarded++;
        rowsWithInvalidColumnCount++;
    }

    public void incrementParseError() {
        rowsDiscarded++;
        rowsWithParseError++;
    }

    public void incrementInactiveRoute() {
        rowsDiscarded++;
        rowsWithInactiveRoute++;
    }

    public void incrementInvalidValues() {
        rowsDiscarded++;
        rowsWithInvalidValues++;
    }

    public void incrementValidPairs() {
        validPairs++;
    }

    public void incrementNegativeDistancePair() {
        discardedPairs++;
        pairsWithNegativeDistance++;
    }

    public void incrementInvalidTimePair() {
        discardedPairs++;
        pairsWithInvalidTime++;
    }

    public void incrementUnrealisticSpeedPair() {
        discardedPairs++;
        pairsWithUnrealisticSpeed++;
    }

    public void incrementOutOfOrderRows() {
        outOfOrderRows++;
    }

    public void setActiveRoutes(int activeRoutes) {
        this.activeRoutes = activeRoutes;
    }

    public void setTrackGroups(int trackGroups) {
        this.trackGroups = trackGroups;
    }

    public void setReportsGenerated(int reportsGenerated) {
        this.reportsGenerated = reportsGenerated;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public long rowsRead() {
        return rowsRead;
    }

    public long rowsAccepted() {
        return rowsAccepted;
    }

    public long rowsDiscarded() {
        return rowsDiscarded;
    }

    public long rowsWithInvalidColumnCount() {
        return rowsWithInvalidColumnCount;
    }

    public long rowsWithParseError() {
        return rowsWithParseError;
    }

    public long rowsWithInactiveRoute() {
        return rowsWithInactiveRoute;
    }

    public long rowsWithInvalidValues() {
        return rowsWithInvalidValues;
    }

    public long validPairs() {
        return validPairs;
    }

    public long discardedPairs() {
        return discardedPairs;
    }

    public long pairsWithNegativeDistance() {
        return pairsWithNegativeDistance;
    }

    public long pairsWithInvalidTime() {
        return pairsWithInvalidTime;
    }

    public long pairsWithUnrealisticSpeed() {
        return pairsWithUnrealisticSpeed;
    }

    public long outOfOrderRows() {
        return outOfOrderRows;
    }

    public int activeRoutes() {
        return activeRoutes;
    }

    public int trackGroups() {
        return trackGroups;
    }

    public int reportsGenerated() {
        return reportsGenerated;
    }

    public long elapsedMs() {
        return elapsedMs;
    }
}
