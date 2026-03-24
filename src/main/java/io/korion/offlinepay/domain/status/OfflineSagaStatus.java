package io.korion.offlinepay.domain.status;

public enum OfflineSagaStatus {
    QUEUED,
    ACCEPTED,
    PROCESSING,
    PARTIALLY_APPLIED,
    COMPLETED,
    COMPENSATION_REQUIRED,
    COMPENSATING,
    COMPENSATED,
    FAILED,
    DEAD_LETTERED
}
