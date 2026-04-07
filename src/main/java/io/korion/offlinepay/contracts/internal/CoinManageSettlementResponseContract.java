package io.korion.offlinepay.contracts.internal;

public record CoinManageSettlementResponseContract(
        String status,
        String message,
        String settlementId,
        String ledgerOutcome,
        String releaseAction,
        boolean duplicated,
        String accountingSide,
        String receiverSettlementMode,
        String settlementModel,
        String reconciliationTrackingOwner,
        String postAvailableBalance,
        String postLockedBalance,
        String postOfflinePayPendingBalance
) {}
