package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import java.time.OffsetDateTime;

public record SettlementBatch(
        String id,
        String sourceDeviceId,
        String idempotencyKey,
        SettlementBatchStatus status,
        int proofsCount,
        String summaryJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}

