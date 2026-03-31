package io.korion.offlinepay.domain.status;

public enum CollateralStatus {
    LOCKED,
    PARTIALLY_SETTLED,
    RELEASED,
    EXPIRED,
    FROZEN;

    public static CollateralStatus fromPersistence(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("collateral status is blank");
        }
        if ("ACTIVE".equalsIgnoreCase(value)) {
            return LOCKED;
        }
        return CollateralStatus.valueOf(value.toUpperCase());
    }
}
