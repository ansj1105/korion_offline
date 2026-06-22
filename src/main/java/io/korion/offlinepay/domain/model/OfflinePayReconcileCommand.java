package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.OfflinePayReconcileCommandStatus;
import java.time.OffsetDateTime;

public record OfflinePayReconcileCommand(
        String id,
        long userId,
        String assetCode,
        String reasonCode,
        String projectionVersion,
        String nonce,
        OfflinePayReconcileCommandStatus status,
        OffsetDateTime expiresAt,
        String deliveredToDeviceId,
        OffsetDateTime deliveredAt,
        String appliedByDeviceId,
        OffsetDateTime appliedAt,
        OffsetDateTime failedAt,
        String errorMessage,
        String dryRunSummaryJson,
        String applySummaryJson,
        String localSummaryJson,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
