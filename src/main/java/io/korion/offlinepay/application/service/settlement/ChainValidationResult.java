package io.korion.offlinepay.application.service.settlement;

public record ChainValidationResult(
        boolean valid,
        String reasonCode,
        String detailJson
) {

    public static ChainValidationResult success() {
        return new ChainValidationResult(true, null, "{}");
    }
}
