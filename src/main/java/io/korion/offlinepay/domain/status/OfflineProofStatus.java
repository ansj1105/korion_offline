package io.korion.offlinepay.domain.status;

public enum OfflineProofStatus {
    UPLOADED,
    VALIDATING,
    VERIFIED_OFFLINE,
    SETTLED,
    REJECTED,
    CONFLICTED,
    EXPIRED,
    FAILED
}
