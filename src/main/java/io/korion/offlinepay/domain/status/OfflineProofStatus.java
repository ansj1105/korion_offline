package io.korion.offlinepay.domain.status;

public enum OfflineProofStatus {
    ISSUED,
    UPLOADED,
    VALIDATING,
    VERIFIED_OFFLINE,
    CONSUMED_PENDING_SETTLEMENT,
    SETTLED,
    REJECTED,
    CONFLICTED,
    EXPIRED,
    FAILED
}
