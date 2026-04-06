package io.korion.offlinepay.interfaces.http.dto;

public record SettlementRequestDetailResponse(
        String settlementId,
        String batchId,
        String status,
        String reasonCode,
        boolean conflictDetected,
        String updatedAt
) {}
