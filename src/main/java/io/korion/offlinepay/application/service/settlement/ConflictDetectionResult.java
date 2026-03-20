package io.korion.offlinepay.application.service.settlement;

public record ConflictDetectionResult(
        boolean conflicted,
        String conflictType,
        String detailJson
) {

    public static ConflictDetectionResult clear() {
        return new ConflictDetectionResult(false, null, "{}");
    }
}
