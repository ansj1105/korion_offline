package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import io.korion.offlinepay.infrastructure.persistence.mapper.OfflineEventLogRowMapper;
import java.math.BigDecimal;
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
                .build();
        jdbcClient.sql(sql)
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
                .update();

        String selectSql = QueryBuilder.select("offline_event_logs")
                .orderBy("created_at DESC")
                .limit(1)
                .build();
        return jdbcClient.sql(selectSql)
                .query(rowMapper)
                .optional()
                .orElseThrow();
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
