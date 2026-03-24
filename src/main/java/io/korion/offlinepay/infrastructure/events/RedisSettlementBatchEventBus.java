package io.korion.offlinepay.infrastructure.events;

import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisSettlementBatchEventBus implements SettlementBatchEventBus {

    private final StringRedisTemplate redisTemplate;
    private final AppProperties properties;

    public RedisSettlementBatchEventBus(StringRedisTemplate redisTemplate, AppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        ensureGroup();
    }

    @Override
    public void publishBatchRequested(String batchId, String uploaderType, String uploaderDeviceId, String requestedAt) {
        redisTemplate.opsForStream().add(
                StringRecord.of(
                        Map.of(
                                "batchId", batchId,
                                "uploaderType", uploaderType,
                                "uploaderDeviceId", uploaderDeviceId,
                                "requestedAt", requestedAt
                        )
                ).withStreamKey(properties.redis().settlementRequestedStream())
        );
    }

    @Override
    public List<QueuedBatchMessage> pollRequestedBatches(int batchSize) {
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                Consumer.from(properties.redis().settlementGroup(), properties.worker().consumerName()),
                StreamReadOptions.empty().count(batchSize).block(Duration.ofMillis(properties.settlementStreamBlockMs())),
                StreamOffset.create(properties.redis().settlementRequestedStream(), ReadOffset.lastConsumed())
        );
        return toQueuedBatchMessages(messages);
    }

    @Override
    public List<QueuedBatchMessage> reclaimStaleRequestedBatches(int batchSize, int minIdleMillis) {
        PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                properties.redis().settlementRequestedStream(),
                properties.redis().settlementGroup(),
                Range.unbounded(),
                batchSize,
                Duration.ofMillis(minIdleMillis)
        );
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return List.of();
        }

        RecordId[] recordIds = pendingRecordIds(pendingMessages);
        if (recordIds.length == 0) {
            return List.of();
        }

        List<MapRecord<String, Object, Object>> claimedMessages = redisTemplate.opsForStream().claim(
                properties.redis().settlementRequestedStream(),
                properties.redis().settlementGroup(),
                properties.worker().consumerName(),
                Duration.ofMillis(minIdleMillis),
                recordIds
        );
        return toQueuedBatchMessages(claimedMessages);
    }

    private RecordId[] pendingRecordIds(PendingMessages pendingMessages) {
        List<RecordId> recordIds = new ArrayList<>();
        for (PendingMessage pendingMessage : pendingMessages) {
            recordIds.add(pendingMessage.getId());
        }
        return recordIds.toArray(RecordId[]::new);
    }

    private List<QueuedBatchMessage> toQueuedBatchMessages(List<MapRecord<String, Object, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<QueuedBatchMessage> queuedBatchMessages = new ArrayList<>();
        for (MapRecord<String, Object, Object> message : messages) {
            Object batchId = message.getValue().get("batchId");
            if (batchId != null) {
                queuedBatchMessages.add(
                        new QueuedBatchMessage(
                                message.getId().getValue(),
                                String.valueOf(batchId),
                                String.valueOf(message.getValue().getOrDefault("uploaderType", "UNKNOWN")),
                                String.valueOf(message.getValue().getOrDefault("uploaderDeviceId", "UNKNOWN"))
                        )
                );
            }
        }
        return queuedBatchMessages;
    }

    @Override
    public void publishBatchResult(String batchId, String status, int settledCount, int failedCount, String processedAt) {
        redisTemplate.opsForStream().add(
                StringRecord.of(
                        Map.of(
                                "batchId", batchId,
                                "status", status,
                                "settledCount", String.valueOf(settledCount),
                                "failedCount", String.valueOf(failedCount),
                                "processedAt", processedAt
                        )
                ).withStreamKey(properties.redis().settlementResultStream())
        );
    }

    @Override
    public void publishExternalSyncRequested(
            String eventType,
            String settlementId,
            String batchId,
            String proofId,
            String payloadJson,
            String requestedAt
    ) {
        throw new UnsupportedOperationException("external sync outbox is only supported by JDBC event bus");
    }

    @Override
    public List<QueuedExternalSyncMessage> pollExternalSyncRequested(int batchSize) {
        return List.of();
    }

    @Override
    public List<QueuedExternalSyncMessage> reclaimStaleExternalSyncRequested(int batchSize, int minIdleMillis) {
        return List.of();
    }

    @Override
    public void publishExternalSyncDeadLetter(
            String eventType,
            String settlementId,
            String batchId,
            String proofId,
            int attemptCount,
            String reasonCode,
            String errorMessage,
            String failedAt
    ) {
        throw new UnsupportedOperationException("external sync outbox is only supported by JDBC event bus");
    }

    @Override
    public void publishConflict(
            String batchId,
            String voucherId,
            String collateralId,
            String conflictType,
            String severity,
            String createdAt
    ) {
        redisTemplate.opsForStream().add(
                StringRecord.of(
                        Map.of(
                                "batchId", batchId,
                                "voucherId", voucherId,
                                "collateralId", collateralId,
                                "conflictType", conflictType,
                                "severity", severity,
                                "createdAt", createdAt
                        )
                ).withStreamKey(properties.redis().settlementConflictStream())
        );
    }

    @Override
    public void publishDeadLetter(String batchId, int attemptCount, String errorMessage, String failedAt) {
        redisTemplate.opsForStream().add(
                StringRecord.of(
                        Map.of(
                                "batchId", batchId,
                                "attemptCount", String.valueOf(attemptCount),
                                "errorMessage", errorMessage,
                                "failedAt", failedAt
                        )
                ).withStreamKey(properties.redis().settlementDeadLetterStream())
        );
    }

    @Override
    public void publishCollateralOperationRequested(
            String operationId,
            String operationType,
            String assetCode,
            String referenceId,
            String requestedAt
    ) {
        redisTemplate.opsForStream().add(
                StringRecord.of(
                        Map.of(
                                "operationId", operationId,
                                "operationType", operationType,
                                "assetCode", assetCode,
                                "referenceId", referenceId,
                                "requestedAt", requestedAt
                        )
                ).withStreamKey(properties.redis().collateralRequestedStream())
        );
    }

    @Override
    public void publishCollateralOperationResult(
            String operationId,
            String operationType,
            String status,
            String assetCode,
            String referenceId,
            String processedAt,
            String errorMessage,
            String reasonCode
    ) {
        redisTemplate.opsForStream().add(
                StringRecord.of(
                        Map.of(
                                "operationId", operationId,
                                "operationType", operationType,
                                "status", status,
                                "assetCode", assetCode,
                                "referenceId", referenceId,
                                "processedAt", processedAt,
                                "errorMessage", errorMessage == null ? "" : errorMessage,
                                "reasonCode", reasonCode == null ? "" : reasonCode
                        )
                ).withStreamKey(properties.redis().collateralResultStream())
        );
    }

    @Override
    public List<QueuedCollateralMessage> pollCollateralOperationRequested(int batchSize) {
        return List.of();
    }

    @Override
    public List<QueuedCollateralMessage> reclaimStaleCollateralOperationRequested(int batchSize, int minIdleMillis) {
        return List.of();
    }

    @Override
    public void acknowledgeRequested(String messageId) {
        redisTemplate.opsForStream().acknowledge(
                properties.redis().settlementGroup(),
                properties.redis().settlementRequestedStream(),
                messageId
        );
    }

    @Override
    public void acknowledgeExternalSync(String messageId) {
        // no-op for redis implementation
    }

    @Override
    public void acknowledgeCollateral(String messageId) {
        // no-op for redis implementation
    }

    private void ensureGroup() {
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(properties.redis().settlementRequestedStream()))) {
                redisTemplate.opsForStream().add(
                        StringRecord.of(Map.of("bootstrap", "true"))
                                .withStreamKey(properties.redis().settlementRequestedStream())
                );
            }
            redisTemplate.opsForStream().createGroup(
                    properties.redis().settlementRequestedStream(),
                    ReadOffset.latest(),
                    properties.redis().settlementGroup()
            );
        } catch (Exception ignored) {
            // group already exists or stream not initialized yet
        }
    }
}
