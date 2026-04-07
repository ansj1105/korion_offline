package io.korion.offlinepay.interfaces.http.dto;

import java.math.BigDecimal;

public record ReconciliationCaseAdminResponse(
        String caseId,
        String settlementId,
        String batchId,
        String proofId,
        String voucherId,
        String caseType,
        String status,
        String reasonCode,
        String createdAt,
        String updatedAt,
        String resolvedAt,
        String sagaStatus,
        String sagaStep,
        String recoveryMode,
        String sagaReasonCode,
        String ledgerOutcome,
        String accountingSide,
        String receiverSettlementMode,
        String settlementModel,
        String reconciliationTrackingOwner,
        Boolean ledgerDuplicated,
        BigDecimal postAvailableBalance,
        BigDecimal postLockedBalance,
        BigDecimal postOfflinePayPendingBalance,
        Boolean retryable,
        String nextAction,
        String eventType,
        String errorMessage,
        String lastManualRetryAt,
        String nextRetryAt
) {}
