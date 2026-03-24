package io.korion.offlinepay.application.port;

import java.util.List;

public interface SettlementBatchEventBus {

    void publishBatchRequested(String batchId, String uploaderType, String uploaderDeviceId, String requestedAt);

    List<QueuedBatchMessage> pollRequestedBatches(int batchSize);

    List<QueuedBatchMessage> reclaimStaleRequestedBatches(int batchSize, int minIdleMillis);

    void publishBatchResult(String batchId, String status, int settledCount, int failedCount, String processedAt);

    void publishExternalSyncRequested(
            String eventType,
            String settlementId,
            String batchId,
            String proofId,
            String payloadJson,
            String requestedAt
    );

    List<QueuedExternalSyncMessage> pollExternalSyncRequested(int batchSize);

    List<QueuedExternalSyncMessage> reclaimStaleExternalSyncRequested(int batchSize, int minIdleMillis);

    void publishExternalSyncDeadLetter(
            String eventType,
            String settlementId,
            String batchId,
            String proofId,
            int attemptCount,
            String reasonCode,
            String errorMessage,
            String failedAt
    );

    void publishConflict(
            String batchId,
            String voucherId,
            String collateralId,
            String conflictType,
            String severity,
            String createdAt
    );

    void publishDeadLetter(String batchId, int attemptCount, String errorMessage, String failedAt);

    void publishCollateralOperationRequested(
            String operationId,
            String operationType,
            String assetCode,
            String referenceId,
            String requestedAt
    );

    void publishCollateralOperationResult(
            String operationId,
            String operationType,
            String status,
            String assetCode,
            String referenceId,
            String processedAt,
            String errorMessage,
            String reasonCode
    );

    void acknowledgeRequested(String messageId);

    void acknowledgeExternalSync(String messageId);

    record QueuedBatchMessage(
            String messageId,
            String batchId,
            String uploaderType,
            String uploaderDeviceId
    ) {}

    record QueuedExternalSyncMessage(
            String messageId,
            String eventType,
            String settlementId,
            String batchId,
            String proofId,
            String payloadJson,
            int attempts
    ) {}
}
