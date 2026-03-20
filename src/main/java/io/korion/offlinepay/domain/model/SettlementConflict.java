package io.korion.offlinepay.domain.model;

import java.time.OffsetDateTime;

public record SettlementConflict(
        String id,
        String settlementId,
        String voucherId,
        String collateralId,
        String deviceId,
        String conflictType,
        String severity,
        String status,
        String detailJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
