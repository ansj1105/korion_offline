package io.korion.offlinepay.domain.status;

public enum SettlementStatus {
    PENDING,
    VALIDATING,
    SETTLED,
    CONFLICT,
    REJECTED,
    EXPIRED
}

