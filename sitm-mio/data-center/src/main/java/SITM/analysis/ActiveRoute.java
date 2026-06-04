package SITM.analysis;

public record ActiveRoute(
        int lineId,
        int planVersionId,
        String shortName,
        String description,
        String activationDate,
        String creationDate) {
}
