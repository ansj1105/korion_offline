package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.CollateralStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CollateralLock(
        String id,
        long userId,
        String deviceId,
        String assetCode,
        BigDecimal lockedAmount,
        BigDecimal remainingAmount,
        String initialStateRoot,
        int policyVersion,
        CollateralStatus status,
        String externalLockId,
        OffsetDateTime expiresAt,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}

