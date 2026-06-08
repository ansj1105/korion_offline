package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.SettlementStatus;
import java.time.OffsetDateTime;

public record SettlementRequest(
        String id,
        String batchId,
        String collateralId,
        String proofId,
        SettlementStatus status,
        String reasonCode,
        boolean conflictDetected,
        String settlementResultJson,
        OffsetDateTime receiverConfirmationDeadlineAt,
        OffsetDateTime receiverConfirmationExpiredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public SettlementRequest(
            String id,
            String batchId,
            String collateralId,
            String proofId,
            SettlementStatus status,
            String reasonCode,
            boolean conflictDetected,
            String settlementResultJson,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                id,
                batchId,
                collateralId,
                proofId,
                status,
                reasonCode,
                conflictDetected,
                settlementResultJson,
                null,
                null,
                createdAt,
                updatedAt
        );
    }
}
