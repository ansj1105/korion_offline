package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CollateralOperation(
        String id,
        String collateralId,
        long userId,
        String deviceId,
        String assetCode,
        CollateralOperationType operationType,
        BigDecimal amount,
        CollateralOperationStatus status,
        String referenceId,
        String errorMessage,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
