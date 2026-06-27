package io.korion.offlinepay.interfaces.http.dto;

import java.math.BigDecimal;

public record SettlementRequestDetailResponse(
        String settlementId,
        String batchId,
        String proofId,
        // Public status projection. PENDING=server validation incomplete, CONFIRMED=server validation
        // succeeded but receiver wallet/history settlement is not finalized, SETTLED=receiver wallet/history
        // settlement finalized, FAILED=terminal validation/settlement failure.
        // Internal saga statuses such as COMPLETED must not be exposed here.
        String status,
        String reasonCode,
        boolean conflictDetected,
        Boolean financiallyHonored,
        String updatedAt,
        // Public saga projection. Internal saga states remain available through sagaStep/recoveryMode.
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
        String settlementModel,
        String reconciliationTrackingOwner,
        Boolean ledgerDuplicated,
        BigDecimal postAvailableBalance,
        BigDecimal postLockedBalance,
        BigDecimal postOfflinePayPendingBalance,
        // receiver confirmation expiry tracking
        String receiverConfirmationDeadlineAt,
        String receiverConfirmationExpiredAt,
        Boolean receiverConfirmationExpired,
        // sender / receiver leg 정산 상태 (saga step 기반)
        // SYNCED | PENDING | N/A
        String senderHistoryStatus,
        String receiverHistoryStatus
) {}
