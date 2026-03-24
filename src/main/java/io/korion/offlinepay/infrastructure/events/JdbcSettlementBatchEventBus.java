package io.korion.offlinepay.infrastructure.events;

import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.service.TelegramAlertService;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcSettlementBatchEventBus implements SettlementBatchEventBus {

    private static final String EVENT_BATCH_REQUESTED = "BATCH_REQUESTED";
    private static final String EVENT_BATCH_RESULT = "BATCH_RESULT";
    private static final String EVENT_CONFLICT = "CONFLICT";
    private static final String EVENT_BATCH_DEAD_LETTER = "BATCH_DEAD_LETTER";
    private static final String EVENT_COLLATERAL_REQUESTED = "COLLATERAL_REQUESTED";
    private static final String EVENT_COLLATERAL_RESULT = "COLLATERAL_RESULT";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    private final JdbcClient jdbcClient;
    private final AppProperties properties;
    private final TelegramAlertService telegramAlertService;

    public JdbcSettlementBatchEventBus(
            JdbcClient jdbcClient,
            AppProperties properties,
            TelegramAlertService telegramAlertService
    ) {
        this.jdbcClient = jdbcClient;
        this.properties = properties;
        this.telegramAlertService = telegramAlertService;
    }

    @Override
    public void publishBatchRequested(String batchId, String uploaderType, String uploaderDeviceId, String requestedAt) {
        insertEvent(
                EVENT_BATCH_REQUESTED,
                STATUS_PENDING,
                batchId,
                uploaderType,
                uploaderDeviceId,
                null,
                null,
                null,
                null,
                json(Map.of(
                        "batchId", batchId,
                        "uploaderType", uploaderType,
                        "uploaderDeviceId", uploaderDeviceId,
                        "requestedAt", requestedAt
                )),
                null,
                null
        );
    }

    @Override
    public List<QueuedBatchMessage> pollRequestedBatches(int batchSize) {
        String sql = """
                WITH claimed AS (
                    SELECT id
                    FROM settlement_outbox_events
                    WHERE event_type = :eventType
                      AND status = :pendingStatus
                    ORDER BY created_at ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE settlement_outbox_events event
                SET status = :processingStatus,
                    lock_owner = :lockOwner,
                    locked_at = NOW(),
                    attempts = event.attempts + 1,
                    updated_at = NOW()
                FROM claimed
                WHERE event.id = claimed.id
                RETURNING event.id, event.batch_id, event.uploader_type, event.uploader_device_id
                """;
        return jdbcClient.sql(sql)
                .param("eventType", EVENT_BATCH_REQUESTED)
                .param("pendingStatus", STATUS_PENDING)
                .param("processingStatus", STATUS_PROCESSING)
                .param("lockOwner", properties.worker().consumerName())
                .param("batchSize", batchSize)
                .query((rs, rowNum) -> new QueuedBatchMessage(
                        rs.getObject("id").toString(),
                        rs.getObject("batch_id").toString(),
                        rs.getString("uploader_type"),
                        rs.getString("uploader_device_id")
                ))
                .list();
    }

    @Override
    public List<QueuedBatchMessage> reclaimStaleRequestedBatches(int batchSize, int minIdleMillis) {
        String sql = """
                WITH claimed AS (
                    SELECT id
                    FROM settlement_outbox_events
                    WHERE event_type = :eventType
                      AND status = :processingStatus
                      AND locked_at IS NOT NULL
                      AND locked_at <= :reclaimBefore
                    ORDER BY locked_at ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE settlement_outbox_events event
                SET lock_owner = :lockOwner,
                    locked_at = NOW(),
                    attempts = event.attempts + 1,
                    updated_at = NOW()
                FROM claimed
                WHERE event.id = claimed.id
                RETURNING event.id, event.batch_id, event.uploader_type, event.uploader_device_id
                """;
        Timestamp reclaimBefore = Timestamp.from(Instant.now().minusMillis(minIdleMillis));
        return jdbcClient.sql(sql)
                .param("eventType", EVENT_BATCH_REQUESTED)
                .param("processingStatus", STATUS_PROCESSING)
                .param("lockOwner", properties.worker().consumerName())
                .param("reclaimBefore", reclaimBefore)
                .param("batchSize", batchSize)
                .query((rs, rowNum) -> new QueuedBatchMessage(
                        rs.getObject("id").toString(),
                        rs.getObject("batch_id").toString(),
                        rs.getString("uploader_type"),
                        rs.getString("uploader_device_id")
                ))
                .list();
    }

    @Override
    public void publishBatchResult(String batchId, String status, int settledCount, int failedCount, String processedAt) {
        insertEvent(
                EVENT_BATCH_RESULT,
                STATUS_COMPLETED,
                batchId,
                null,
                null,
                null,
                null,
                null,
                null,
                json(Map.of(
                        "batchId", batchId,
                        "status", status,
                        "settledCount", settledCount,
                        "failedCount", failedCount,
                        "processedAt", processedAt
                )),
                null,
                null
        );
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
        insertEvent(
                EVENT_CONFLICT,
                STATUS_COMPLETED,
                batchId,
                null,
                null,
                null,
                conflictType,
                null,
                voucherId,
                json(Map.of(
                        "batchId", batchId,
                        "voucherId", voucherId,
                        "collateralId", collateralId,
                        "conflictType", conflictType,
                        "severity", severity,
                        "createdAt", createdAt
                )),
                null,
                null
        );
    }

    @Override
    public void publishDeadLetter(String batchId, int attemptCount, String errorMessage, String failedAt) {
        insertEvent(
                EVENT_BATCH_DEAD_LETTER,
                STATUS_DEAD_LETTER,
                batchId,
                null,
                null,
                null,
                null,
                null,
                null,
                json(Map.of(
                        "batchId", batchId,
                        "attemptCount", attemptCount,
                        "errorMessage", errorMessage,
                        "failedAt", failedAt
                )),
                OfflinePayReasonCode.BATCH_SYNC_FAIL,
                errorMessage
        );
        telegramAlertService.notifyCircuitOpened(
                "offline_pay.settlement_worker.dead_letter",
                "batchId=" + batchId + ", attempts=" + attemptCount + ", error=" + normalize(errorMessage)
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
        insertEvent(
                EVENT_COLLATERAL_REQUESTED,
                STATUS_COMPLETED,
                null,
                null,
                null,
                operationId,
                operationType,
                assetCode,
                referenceId,
                json(Map.of(
                        "operationId", operationId,
                        "operationType", operationType,
                        "assetCode", assetCode,
                        "referenceId", referenceId,
                        "requestedAt", requestedAt
                )),
                null,
                null
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
            String errorMessage
    ) {
        insertEvent(
                EVENT_COLLATERAL_RESULT,
                STATUS_COMPLETED,
                null,
                null,
                null,
                operationId,
                operationType,
                assetCode,
                referenceId,
                json(Map.of(
                        "operationId", operationId,
                        "operationType", operationType,
                        "status", status,
                        "assetCode", assetCode,
                        "referenceId", referenceId,
                        "processedAt", processedAt,
                        "errorMessage", normalize(errorMessage)
                )),
                null,
                errorMessage
        );
    }

    @Override
    public void acknowledgeRequested(String messageId) {
        String sql = """
                UPDATE settlement_outbox_events
                SET status = :completedStatus,
                    processed_at = NOW(),
                    updated_at = NOW()
                WHERE id = :id
                """;
        jdbcClient.sql(sql)
                .param("completedStatus", STATUS_COMPLETED)
                .param("id", java.util.UUID.fromString(messageId))
                .update();
    }

    private void insertEvent(
            String eventType,
            String status,
            String batchId,
            String uploaderType,
            String uploaderDeviceId,
            String operationId,
            String operationType,
            String assetCode,
            String referenceId,
            String payloadJson,
            String reasonCode,
            String errorMessage
    ) {
        String sql = """
                INSERT INTO settlement_outbox_events (
                    event_type,
                    status,
                    batch_id,
                    uploader_type,
                    uploader_device_id,
                    operation_id,
                    operation_type,
                    asset_code,
                    reference_id,
                    payload,
                    reason_code,
                    error_message,
                    processed_at,
                    dead_lettered_at
                ) VALUES (
                    :eventType,
                    :status,
                    :batchId,
                    :uploaderType,
                    :uploaderDeviceId,
                    :operationId,
                    :operationType,
                    :assetCode,
                    :referenceId,
                    CAST(:payload AS jsonb),
                    :reasonCode,
                    :errorMessage,
                    CASE WHEN :status = :completedStatus THEN NOW() ELSE NULL END,
                    CASE WHEN :status = :deadLetterStatus THEN NOW() ELSE NULL END
                )
                """;
        jdbcClient.sql(sql)
                .param("eventType", eventType)
                .param("status", status)
                .param("batchId", batchId == null ? null : java.util.UUID.fromString(batchId))
                .param("uploaderType", uploaderType)
                .param("uploaderDeviceId", uploaderDeviceId)
                .param("operationId", operationId == null ? null : java.util.UUID.fromString(operationId))
                .param("operationType", operationType)
                .param("assetCode", assetCode)
                .param("referenceId", referenceId)
                .param("payload", payloadJson)
                .param("reasonCode", reasonCode)
                .param("errorMessage", errorMessage)
                .param("completedStatus", STATUS_COMPLETED)
                .param("deadLetterStatus", STATUS_DEAD_LETTER)
                .update();
    }

    private String json(Map<String, ?> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(entry.getKey()).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
