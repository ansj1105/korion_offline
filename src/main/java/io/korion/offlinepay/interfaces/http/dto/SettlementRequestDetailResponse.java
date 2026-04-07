package io.korion.offlinepay.interfaces.http.dto;

import java.math.BigDecimal;

public record SettlementRequestDetailResponse(
        String settlementId,
        String batchId,
        String status,
        String reasonCode,
        boolean conflictDetected,
        String updatedAt,
        String sagaStatus,
        String sagaStep,
        String recoveryMode,
        String sagaReasonCode,
        String reconciliationCaseType,
        String reconciliationStatus,
        String reconciliationReasonCode,
        // proof
        String senderDeviceId,
        String receiverDeviceId,
        BigDecimal proofAmount,
        String channelType,
        // collateral snapshot at query time (post-settlement if SETTLED)
        BigDecimal collateralLockedAmount,
        BigDecimal collateralRemainingAmount,
        // coin_manage ledger sync outcome
        String ledgerOutcome,
        String accountingSide,
        String receiverSettlementMode,
        Boolean ledgerDuplicated,
        BigDecimal postAvailableBalance,
        BigDecimal postLockedBalance,
        BigDecimal postOfflinePayPendingBalance
) {}
