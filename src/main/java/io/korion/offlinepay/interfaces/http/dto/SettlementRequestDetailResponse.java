package io.korion.offlinepay.interfaces.http.dto;

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
        String reconciliationReasonCode
) {}
