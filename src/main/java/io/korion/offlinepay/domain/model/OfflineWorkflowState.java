package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.OfflineRecoveryMode;
import io.korion.offlinepay.domain.status.OfflineWorkflowStage;
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
) {
    public String recoveryMode() {
        return parseWorkflowStage().preferredRecoveryMode().name();
    }

    public boolean savePoint() {
        return parseWorkflowStage().isSavePoint();
    }

    public boolean terminal() {
        OfflineRecoveryMode recoveryMode = parseWorkflowStage().preferredRecoveryMode();
        return recoveryMode == OfflineRecoveryMode.TERMINAL || recoveryMode == OfflineRecoveryMode.NONE;
    }

    private OfflineWorkflowStage parseWorkflowStage() {
        String normalized = workflowStage == null || workflowStage.isBlank()
                ? OfflineWorkflowStage.FAILED.name()
                : workflowStage.trim().toUpperCase();
        return OfflineWorkflowStage.valueOf(normalized);
    }
}
