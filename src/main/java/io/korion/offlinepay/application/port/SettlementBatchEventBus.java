package io.korion.offlinepay.application.port;

import java.util.List;

public interface SettlementBatchEventBus {

    void publishBatchRequested(String batchId, String uploaderType, String uploaderDeviceId, String requestedAt);

    List<QueuedBatchMessage> pollRequestedBatches(int batchSize);

    List<QueuedBatchMessage> reclaimStaleRequestedBatches(int batchSize, int minIdleMillis);

    void publishBatchResult(String batchId, String status, int settledCount, int failedCount, String processedAt);

    void publishConflict(
            String batchId,
            String voucherId,
            String collateralId,
            String conflictType,
            String severity,
            String createdAt
    );

    void publishDeadLetter(String batchId, int attemptCount, String errorMessage, String failedAt);

    void acknowledgeRequested(String messageId);

    record QueuedBatchMessage(
            String messageId,
            String batchId,
            String uploaderType,
            String uploaderDeviceId
    ) {}
}
