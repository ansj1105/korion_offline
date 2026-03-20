package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.infrastructure.persistence.mapper.CollateralLockRowMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
                .build();
        jdbcClient.sql(sql.replace(":metadata", "CAST(:metadata AS jsonb)"))
                .param("userId", userId)
                .param("deviceId", deviceId)
                .param("assetCode", assetCode)
                .param("lockedAmount", lockedAmount)
                .param("remainingAmount", remainingAmount)
                .param("initialStateRoot", initialStateRoot)
                .param("policyVersion", policyVersion)
                .param("status", status.name())
                .param("externalLockId", externalLockId)
                .param("expiresAt", expiresAt)
                .param("metadata", metadataJson)
                .update();

        String selectSql = QueryBuilder
                .select("collateral_locks")
                .where("device_id = :deviceId")
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
                .where("id = :id")
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(collateralId))
                .query(collateralLockRowMapper)
                .optional();
    }

    @Override
    public void deductRemainingAmount(String collateralId, BigDecimal amount) {
        String sql = QueryBuilder.update("collateral_locks")
                .set("remaining_amount = GREATEST(remaining_amount - :amount, 0)")
                .touchUpdatedAt()
                .where("id = :id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(collateralId))
                .param("amount", amount)
                .update();
    }

    @Override
    public void updateStatus(String collateralId, CollateralStatus status, String metadataJson) {
        String sql = QueryBuilder.update("collateral_locks")
                .set("status = :status")
                .set("metadata = metadata || CAST(:metadata AS jsonb)")
                .touchUpdatedAt()
                .where("id = :id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(collateralId))
                .param("status", status.name())
                .param("metadata", metadataJson)
                .update();
    }
}
