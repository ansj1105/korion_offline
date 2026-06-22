package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflinePayReconcileCommandRepository;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.OfflinePayReconcileCommand;
import io.korion.offlinepay.domain.status.OfflinePayReconcileCommandStatus;
import io.korion.offlinepay.infrastructure.persistence.mapper.OfflinePayReconcileCommandRowMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOfflinePayReconcileCommandRepository implements OfflinePayReconcileCommandRepository {

    private final JdbcClient jdbcClient;
    private final JsonService jsonService;
    private final OfflinePayReconcileCommandRowMapper rowMapper;

    public JdbcOfflinePayReconcileCommandRepository(
            JdbcClient jdbcClient,
            JsonService jsonService,
            OfflinePayReconcileCommandRowMapper rowMapper
    ) {
        this.jdbcClient = jdbcClient;
        this.jsonService = jsonService;
        this.rowMapper = rowMapper;
    }

    @Override
    public OfflinePayReconcileCommand create(
            long userId,
            String assetCode,
            String reasonCode,
            String projectionVersion,
            String nonce,
            OffsetDateTime expiresAt,
            Map<String, Object> metadata
    ) {
        String sql = QueryBuilder.insert(
                        "offline_pay_reconcile_commands",
                        "user_id",
                        "asset_code",
                        "reason_code",
                        "projection_version",
                        "nonce",
                        "expires_at",
                        "metadata"
                )
                .value("metadata", "CAST(:metadata AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("user_id", userId)
                .param("asset_code", assetCode)
                .param("reason_code", reasonCode)
                .param("projection_version", projectionVersion)
                .param("nonce", nonce)
                .param("expires_at", expiresAt)
                .param("metadata", jsonService.write(metadata))
                .update();
        return findByNonce(nonce).orElseThrow();
    }

    @Override
    public Optional<OfflinePayReconcileCommand> findRunnableByUserIdAndAssetCode(long userId, String assetCode, OffsetDateTime now) {
        String sql = """
                SELECT *
                FROM offline_pay_reconcile_commands
                WHERE user_id = :userId
                  AND asset_code = :assetCode
                  AND status IN ('PENDING', 'DELIVERED')
                  AND expires_at > :now
                ORDER BY created_at DESC
                LIMIT 1
                """;
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("assetCode", assetCode)
                .param("now", now)
                .query(rowMapper)
                .optional();
    }

    @Override
    public Optional<OfflinePayReconcileCommand> findByIdAndNonce(String id, String nonce) {
        String sql = QueryBuilder.select("offline_pay_reconcile_commands")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .where("nonce", QueryBuilder.Op.EQ, ":nonce")
                .limit(1)
                .build();
        return jdbcClient.sql(sql)
                .param("id", UUID.fromString(id))
                .param("nonce", nonce)
                .query(rowMapper)
                .optional();
    }

    @Override
    public OfflinePayReconcileCommand markDelivered(String id, String deviceId) {
        String sql = QueryBuilder.update("offline_pay_reconcile_commands")
                .set("status", "CASE WHEN status = 'PENDING' THEN 'DELIVERED' ELSE status END")
                .set("delivered_to_device_id", ":deviceId")
                .set("delivered_at", "COALESCE(delivered_at, NOW())")
                .touchUpdatedAt()
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("id", UUID.fromString(id))
                .param("deviceId", deviceId)
                .update();
        return findById(id).orElseThrow();
    }

    @Override
    public OfflinePayReconcileCommand markReported(
            String id,
            OfflinePayReconcileCommandStatus status,
            String deviceId,
            Map<String, Object> dryRunSummary,
            Map<String, Object> applySummary,
            Map<String, Object> localSummary,
            String errorMessage
    ) {
        String sql = QueryBuilder.update("offline_pay_reconcile_commands")
                .set("status", ":status")
                .set("applied_by_device_id", ":deviceId")
                .set("applied_at", "CASE WHEN :status = 'APPLIED' THEN NOW() ELSE applied_at END")
                .set("failed_at", "CASE WHEN :status = 'FAILED' THEN NOW() ELSE failed_at END")
                .set("error_message", ":errorMessage")
                .set("dry_run_summary", "CAST(:dryRunSummary AS jsonb)")
                .set("apply_summary", "CAST(:applySummary AS jsonb)")
                .set("local_summary", "CAST(:localSummary AS jsonb)")
                .touchUpdatedAt()
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("id", UUID.fromString(id))
                .param("status", status.name())
                .param("deviceId", deviceId)
                .param("errorMessage", errorMessage)
                .param("dryRunSummary", jsonService.write(dryRunSummary))
                .param("applySummary", jsonService.write(applySummary))
                .param("localSummary", jsonService.write(localSummary))
                .update();
        return findById(id).orElseThrow();
    }

    private Optional<OfflinePayReconcileCommand> findById(String id) {
        String sql = QueryBuilder.select("offline_pay_reconcile_commands")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .limit(1)
                .build();
        return jdbcClient.sql(sql)
                .param("id", UUID.fromString(id))
                .query(rowMapper)
                .optional();
    }

    private Optional<OfflinePayReconcileCommand> findByNonce(String nonce) {
        String sql = QueryBuilder.select("offline_pay_reconcile_commands")
                .where("nonce", QueryBuilder.Op.EQ, ":nonce")
                .limit(1)
                .build();
        return jdbcClient.sql(sql)
                .param("nonce", nonce)
                .query(rowMapper)
                .optional();
    }
}
