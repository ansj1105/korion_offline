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
    DEAD_LETTERED
}
