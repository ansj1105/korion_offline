package io.korion.offlinepay.application.port;

import java.math.BigDecimal;

public interface CoinManageSettlementPort {

    SettlementLedgerResult finalizeSettlement(SettlementLedgerCommand command);

    SettlementLedgerResult compensateSettlement(SettlementCompensationCommand command);

    record SettlementLedgerCommand(
            String settlementId,
            String batchId,
            String collateralId,
            String proofId,
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal amount,
            String settlementStatus,
            String releaseAction,
            boolean conflictDetected,
            String proofFingerprint,
            String newStateHash,
            String previousHash,
            long monotonicCounter,
            String nonce,
            String signature
    ) {}

    record SettlementCompensationCommand(
            String settlementId,
            String batchId,
            String collateralId,
            String proofId,
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal amount,
            String releaseAction,
            String proofFingerprint,
            String compensationReason
    ) {}

    record SettlementLedgerResult(
            String settlementId,
            String ledgerOutcome,
            String releaseAction,
            boolean duplicated,
            String accountingSide,
            String receiverSettlementMode,
            String settlementModel,
            String reconciliationTrackingOwner,
            BigDecimal postAvailableBalance,
            BigDecimal postLockedBalance,
            BigDecimal postOfflinePayPendingBalance
    ) {}
}
