package io.korion.offlinepay.domain.model;

import java.time.OffsetDateTime;

public record OfflineWorkflowState(
        String id,
        String workflowType,
        String workflowId,
        String workflowStage,
        String eventType,
        String sourceEventId,
        String batchId,
        String settlementId,
        String operationId,
        String proofId,
        String referenceId,
        String assetCode,
        String reasonCode,
        String errorMessage,
        String payloadJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
