package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import io.korion.offlinepay.infrastructure.persistence.mapper.OfflineEventLogRowMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOfflineEventLogRepository implements OfflineEventLogRepository {

    private final JdbcClient jdbcClient;
    private final OfflineEventLogRowMapper rowMapper;

    public JdbcOfflineEventLogRepository(JdbcClient jdbcClient, OfflineEventLogRowMapper rowMapper) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public OfflineEventLog save(
            long userId,
            String deviceId,
            OfflineEventType eventType,
            OfflineEventStatus eventStatus,
            String assetCode,
            String networkCode,
            BigDecimal amount,
            String requestId,
            String settlementId,
            String counterpartyDeviceId,
            String counterpartyActor,
            String reasonCode,
            String message,
            String metadataJson
    ) {
        validateReasonPolicy(eventType, eventStatus, reasonCode);
        String sql = QueryBuilder.insert(
                        "offline_event_logs",
                        "user_id",
                        "device_id",
                        "event_type",
                        "event_status",
                        "asset_code",
                        "network_code",
                        "amount",
                        "request_id",
                        "settlement_id",
                        "counterparty_device_id",
                        "counterparty_actor",
                        "reason_code",
                        "message",
                        "metadata"
                )
                .value("metadata", "CAST(:metadata AS jsonb)")
                .build()
                + """
                ON CONFLICT (request_id, event_type, event_status) WHERE request_id IS NOT NULL
                DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    device_id = EXCLUDED.device_id,
                    asset_code = EXCLUDED.asset_code,
                    network_code = EXCLUDED.network_code,
                    amount = EXCLUDED.amount,
                    settlement_id = EXCLUDED.settlement_id,
                    counterparty_device_id = EXCLUDED.counterparty_device_id,
                    counterparty_actor = EXCLUDED.counterparty_actor,
                    reason_code = EXCLUDED.reason_code,
                    message = EXCLUDED.message,
                    metadata = EXCLUDED.metadata,
                    updated_at = NOW()
                RETURNING *
                """;
        return jdbcClient.sql(sql)
                .param("user_id", userId)
                .param("device_id", deviceId)
                .param("event_type", eventType.name())
                .param("event_status", eventStatus.name())
                .param("asset_code", assetCode)
                .param("network_code", networkCode)
                .param("amount", amount)
                .param("request_id", requestId)
                .param("settlement_id", settlementId)
                .param("counterparty_device_id", counterpartyDeviceId)
                .param("counterparty_actor", counterpartyActor)
                .param("reason_code", reasonCode)
                .param("message", message)
                .param("metadata", metadataJson)
                .query(rowMapper)
                .optional()
                .orElseThrow();
    }

    @Override
    public int closePendingByRequestId(
            String requestId,
            OfflineEventStatus terminalStatus,
            String reasonCode
    ) {
        if (requestId == null || requestId.isBlank() || terminalStatus == OfflineEventStatus.PENDING) {
            return 0;
        }
        String sql = """
                UPDATE offline_event_logs pending
                SET
                    event_status = :terminalStatus,
                    reason_code = COALESCE(NULLIF(pending.reason_code, ''), :reasonCode),
                    updated_at = NOW()
                WHERE pending.request_id = :requestId
                  AND pending.event_status = :pendingStatus
                  AND NOT EXISTS (
                      SELECT 1
                      FROM offline_event_logs terminal
                      WHERE terminal.request_id = pending.request_id
                        AND terminal.event_type = pending.event_type
                        AND terminal.event_status = :terminalStatus
                  )
                """;
        return jdbcClient.sql(sql)
                .param("requestId", requestId)
                .param("terminalStatus", terminalStatus.name())
                .param("pendingStatus", OfflineEventStatus.PENDING.name())
                .param("reasonCode", reasonCode == null ? "" : reasonCode)
                .update();
    }

    @Override
    public int closePendingResolvedByTerminalEvents() {
        String sql = """
                UPDATE offline_event_logs pending
                SET
                    event_status = terminal.event_status,
                    reason_code = COALESCE(NULLIF(pending.reason_code, ''), terminal.reason_code),
                    updated_at = NOW()
                FROM (
                    SELECT DISTINCT ON (request_id)
                        request_id,
                        event_status,
                        reason_code,
                        updated_at
                    FROM offline_event_logs
                    WHERE request_id IS NOT NULL
                      AND event_status <> :pendingStatus
                    ORDER BY request_id, updated_at DESC
                ) terminal
                WHERE pending.request_id = terminal.request_id
                  AND pending.event_status = :pendingStatus
                  AND NOT EXISTS (
                      SELECT 1
                      FROM offline_event_logs existing_terminal
                      WHERE existing_terminal.request_id = pending.request_id
                        AND existing_terminal.event_type = pending.event_type
                        AND existing_terminal.event_status = terminal.event_status
                  )
                """;
        return jdbcClient.sql(sql)
                .param("pendingStatus", OfflineEventStatus.PENDING.name())
                .update();
    }

    @Override
    public int expirePendingOlderThan(OffsetDateTime cutoff, String reasonCode) {
        if (cutoff == null) {
            return 0;
        }
        String sql = """
                UPDATE offline_event_logs pending
                SET
                    event_status = :failedStatus,
                    reason_code = COALESCE(NULLIF(pending.reason_code, ''), :reasonCode),
                    updated_at = NOW()
                WHERE pending.event_status = :pendingStatus
                  AND pending.created_at < :cutoff
                  AND NOT EXISTS (
                      SELECT 1
                      FROM offline_event_logs terminal
                      WHERE terminal.request_id = pending.request_id
                        AND terminal.event_status <> :pendingStatus
                  )
                """;
        return jdbcClient.sql(sql)
                .param("failedStatus", OfflineEventStatus.FAILED.name())
                .param("pendingStatus", OfflineEventStatus.PENDING.name())
                .param("cutoff", cutoff)
                .param("reasonCode", reasonCode == null ? "" : reasonCode)
                .update();
    }

    @Override
    public List<OfflineEventLog> findRecent(
            int size,
            OfflineEventType eventType,
            OfflineEventStatus eventStatus,
            String assetCode
    ) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("offline_event_logs")
                .orderBy("created_at DESC")
                .limit(size);
        if (eventType != null) {
            builder.where("event_type", QueryBuilder.Op.EQ, ":eventType");
        }
        if (eventStatus != null) {
            builder.where("event_status", QueryBuilder.Op.EQ, ":eventStatus");
        }
        if (assetCode != null && !assetCode.isBlank()) {
            builder.where("asset_code", QueryBuilder.Op.EQ, ":assetCode");
        }

        JdbcClient.StatementSpec spec = jdbcClient.sql(builder.build());
        if (eventType != null) {
            spec.param("eventType", eventType.name());
        }
        if (eventStatus != null) {
            spec.param("eventStatus", eventStatus.name());
        }
        if (assetCode != null && !assetCode.isBlank()) {
            spec.param("assetCode", assetCode.toUpperCase());
        }
        return spec.query(rowMapper).list();
    }

    private void validateReasonPolicy(OfflineEventType eventType, OfflineEventStatus eventStatus, String reasonCode) {
        boolean failedStatus = eventStatus == OfflineEventStatus.FAILED;
        boolean failedType = switch (eventType) {
            case NFC_CONNECT_FAIL,
                    BLE_SCAN_FAIL,
                    BLE_PAIR_FAIL,
                    QR_PARSE_FAIL,
                    AUTH_BIOMETRIC_FAIL,
                    AUTH_PIN_FAIL,
                    AUTH_CANCELLED,
                    PROOF_NOT_FOUND,
                    PROOF_EXPIRED,
                    PROOF_TAMPERED,
                    PAYLOAD_BUILD_FAIL,
                    SEND_TIMEOUT,
                    SEND_INTERRUPTED,
                    RECEIVE_REJECTED,
                    LOCAL_QUEUE_SAVE_FAIL,
                    BATCH_SYNC_FAIL,
                    LEDGER_SYNC_FAIL,
                    HISTORY_SYNC_FAIL,
                    LEDGER_CIRCUIT_OPEN,
                    HISTORY_CIRCUIT_OPEN,
                    SERVER_VALIDATION_FAIL,
                    SETTLEMENT_FAIL,
                    SYNC_FAILED,
                    SETTLEMENT_FAILED,
                    TRANSPORT_FAILED -> true;
            default -> false;
        };
        if ((failedStatus || failedType) && (reasonCode == null || reasonCode.isBlank())) {
            throw new IllegalStateException("reasonCode is required for failed offline event logs");
        }
    }
}
