package io.korion.offlinepay.domain.status;

public enum SettlementBatchStatus {
    CREATED,
    UPLOADED,
    VALIDATING,
    PARTIALLY_SETTLED,
    SETTLED,
    FAILED,
    CLOSED
}

