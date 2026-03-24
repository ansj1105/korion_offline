package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflineWorkflowState;
import java.util.List;

public interface OfflineWorkflowStateRepository {

    OfflineWorkflowState upsert(
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
            String payloadJson
    );

    List<OfflineWorkflowState> findRecent(int limit, String workflowType, String workflowStage);
}
