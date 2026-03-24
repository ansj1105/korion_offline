package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.infrastructure.persistence.mapper.CollateralLockRowMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCollateralRepository implements CollateralRepository {

    private final JdbcClient jdbcClient;
    private final CollateralLockRowMapper collateralLockRowMapper;

    public JdbcCollateralRepository(JdbcClient jdbcClient, CollateralLockRowMapper collateralLockRowMapper) {
        this.jdbcClient = jdbcClient;
        this.collateralLockRowMapper = collateralLockRowMapper;
    }

    @Override
    public CollateralLock save(
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal lockedAmount,
            BigDecimal remainingAmount,
            String initialStateRoot,
            int policyVersion,
            CollateralStatus status,
            String externalLockId,
            OffsetDateTime expiresAt,
            String metadataJson
    ) {
        String sql = QueryBuilder
                .insert(
                        "collateral_locks",
                        "user_id",
                        "device_id",
                        "asset_code",
                        "locked_amount",
                        "remaining_amount",
                        "initial_state_root",
                        "policy_version",
                        "status",
                        "external_lock_id",
                        "expires_at",
                        "metadata"
                )
                .value("metadata", "CAST(:metadata AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("user_id", userId)
                .param("device_id", deviceId)
                .param("asset_code", assetCode)
                .param("locked_amount", lockedAmount)
                .param("remaining_amount", remainingAmount)
                .param("initial_state_root", initialStateRoot)
                .param("policy_version", policyVersion)
                .param("status", status.name())
                .param("external_lock_id", externalLockId)
                .param("expires_at", expiresAt)
                .param("metadata", metadataJson)
                .update();

        String selectSql = QueryBuilder
                .select("collateral_locks")
                .where("device_id", QueryBuilder.Op.EQ, ":deviceId")
                .orderBy("created_at DESC")
                .limit(1)
                .build();
        return jdbcClient.sql(selectSql)
                .param("deviceId", deviceId)
                .query(collateralLockRowMapper)
                .single();
    }

    @Override
    public Optional<CollateralLock> findById(String collateralId) {
        String sql = QueryBuilder.select("collateral_locks")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(collateralId))
                .query(collateralLockRowMapper)
                .optional();
    }

    @Override
    public Optional<CollateralLock> findLatestByUserIdAndDeviceIdAndAssetCode(long userId, String deviceId, String assetCode) {
        String sql = QueryBuilder.select("collateral_locks")
                .where("user_id", QueryBuilder.Op.EQ, ":userId")
                .where("device_id", QueryBuilder.Op.EQ, ":deviceId")
                .where("asset_code", QueryBuilder.Op.EQ, ":assetCode")
                .orderBy("updated_at DESC")
                .limit(1)
                .build();
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("deviceId", deviceId)
                .param("assetCode", assetCode)
                .query(collateralLockRowMapper)
                .optional();
    }

    @Override
    public Optional<CollateralLock> findAggregateByUserIdAndDeviceIdAndAssetCode(long userId, String deviceId, String assetCode) {
        String sql = """
                SELECT
                    MIN(id::text) AS id,
                    user_id,
                    device_id,
                    asset_code,
                    COALESCE(SUM(locked_amount), 0) AS locked_amount,
                    COALESCE(SUM(remaining_amount), 0) AS remaining_amount,
                    'AGGREGATED' AS initial_state_root,
                    COALESCE(MAX(policy_version), 1) AS policy_version,
                    CASE
                        WHEN BOOL_OR(status = 'LOCKED') THEN 'LOCKED'
                        ELSE COALESCE(MAX(status), 'RELEASED')
                    END AS status,
                    STRING_AGG(external_lock_id, ',' ORDER BY created_at) AS external_lock_id,
                    MAX(expires_at) AS expires_at,
                    '{}'::text AS metadata,
                    MIN(created_at) AS created_at,
                    MAX(updated_at) AS updated_at
                FROM collateral_locks
                WHERE user_id = :userId
                  AND device_id = :deviceId
                  AND asset_code = :assetCode
                  AND remaining_amount > 0
                GROUP BY user_id, device_id, asset_code
                """;
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("deviceId", deviceId)
                .param("assetCode", assetCode)
                .query(collateralLockRowMapper)
                .optional();
    }

    @Override
    public List<CollateralLock> findActiveByUserIdAndDeviceIdAndAssetCode(long userId, String deviceId, String assetCode) {
        String sql = QueryBuilder.select("collateral_locks")
                .where("user_id", QueryBuilder.Op.EQ, ":userId")
                .where("device_id", QueryBuilder.Op.EQ, ":deviceId")
                .where("asset_code", QueryBuilder.Op.EQ, ":assetCode")
                .where("remaining_amount", QueryBuilder.Op.GT, ":zero")
                .orderBy("created_at ASC")
                .build();
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("deviceId", deviceId)
                .param("assetCode", assetCode)
                .param("zero", BigDecimal.ZERO)
                .query(collateralLockRowMapper)
                .list();
    }

    @Override
    public void deductRemainingAmount(String collateralId, BigDecimal amount) {
        String sql = QueryBuilder.update("collateral_locks")
                .set("remaining_amount", "GREATEST(remaining_amount - :amount, 0)")
                .touchUpdatedAt()
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(collateralId))
                .param("amount", amount)
                .update();
    }

    @Override
    public void updateStatus(String collateralId, CollateralStatus status, String metadataJson) {
        String sql = QueryBuilder.update("collateral_locks")
                .set("status", ":status")
                .set("metadata", "metadata || CAST(:metadata AS jsonb)")
                .touchUpdatedAt()
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(collateralId))
                .param("status", status.name())
                .param("metadata", metadataJson)
                .update();
    }
}
