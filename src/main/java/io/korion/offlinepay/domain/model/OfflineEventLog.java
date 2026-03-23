package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OfflineEventLog(
        String id,
        long userId,
        String deviceId,
        OfflineEventType eventType,
        OfflineEventStatus eventStatus,
        String assetCode,
        String networkCode,
        BigDecimal amount,
        String requestId,
        String settlementId,
        String counterpartyDeviceId,
        String counterpartyActor,
        String reasonCode,
        String message,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
