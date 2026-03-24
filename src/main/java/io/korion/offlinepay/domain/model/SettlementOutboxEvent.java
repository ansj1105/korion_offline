package io.korion.offlinepay.domain.model;

import java.time.OffsetDateTime;

public record SettlementOutboxEvent(
        String id,
        String eventType,
        String status,
        String batchId,
        String uploaderType,
        String uploaderDeviceId,
        String payloadJson,
        int attempts,
        String lockOwner,
        OffsetDateTime lockedAt,
        OffsetDateTime processedAt,
        OffsetDateTime deadLetteredAt,
        String reasonCode,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
