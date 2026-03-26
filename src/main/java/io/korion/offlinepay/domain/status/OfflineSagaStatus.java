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
    DEAD_LETTERED;

    public OfflineRecoveryMode recoveryMode() {
        return switch (this) {
            case QUEUED, ACCEPTED, PROCESSING, PARTIALLY_APPLIED -> OfflineRecoveryMode.FORWARD_RECOVERY;
            case COMPENSATION_REQUIRED, COMPENSATING -> OfflineRecoveryMode.BACKWARD_RECOVERY;
            case FAILED, DEAD_LETTERED -> OfflineRecoveryMode.TERMINAL;
            case COMPLETED, COMPENSATED -> OfflineRecoveryMode.NONE;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED || this == FAILED || this == DEAD_LETTERED;
    }
}
