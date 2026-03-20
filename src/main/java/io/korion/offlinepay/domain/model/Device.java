package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.DeviceStatus;
import java.time.OffsetDateTime;

public record Device(
        String id,
        String deviceId,
        long userId,
        String publicKey,
        int keyVersion,
        DeviceStatus status,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}

