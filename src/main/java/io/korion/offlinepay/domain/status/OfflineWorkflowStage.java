package io.korion.offlinepay.domain.status;

public enum OfflineWorkflowStage {
    LOCAL_QUEUED,
    SERVER_ACCEPTED,
    SETTLEMENT_ACCEPTED,
    COLLATERAL_LOCKED,
    COLLATERAL_RELEASED,
    LEDGER_SYNCED,
    COMPENSATING,
    COMPENSATED,
    HISTORY_SYNCED,
    FAILED,
    DEAD_LETTERED;

    public boolean isSavePoint() {
        return switch (this) {
            case SERVER_ACCEPTED, SETTLEMENT_ACCEPTED, COLLATERAL_LOCKED, COLLATERAL_RELEASED, LEDGER_SYNCED, HISTORY_SYNCED -> true;
            default -> false;
        };
    }

    public OfflineRecoveryMode preferredRecoveryMode() {
        return switch (this) {
            case LOCAL_QUEUED, SERVER_ACCEPTED, SETTLEMENT_ACCEPTED -> OfflineRecoveryMode.FORWARD_RECOVERY;
            case COLLATERAL_LOCKED, COLLATERAL_RELEASED, LEDGER_SYNCED, COMPENSATING -> OfflineRecoveryMode.BACKWARD_RECOVERY;
            case COMPENSATED, HISTORY_SYNCED -> OfflineRecoveryMode.NONE;
            case FAILED, DEAD_LETTERED -> OfflineRecoveryMode.TERMINAL;
        };
    }
}
