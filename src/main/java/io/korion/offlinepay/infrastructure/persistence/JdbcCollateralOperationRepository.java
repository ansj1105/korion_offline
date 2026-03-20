package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.infrastructure.persistence.mapper.CollateralOperationRowMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCollateralOperationRepository implements CollateralOperationRepository {

    private final JdbcClient jdbcClient;
    private final CollateralOperationRowMapper rowMapper;

    public JdbcCollateralOperationRepository(JdbcClient jdbcClient, CollateralOperationRowMapper rowMapper) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public CollateralOperation saveRequested(
            String collateralId,
            long userId,
            String deviceId,
            String assetCode,
            CollateralOperationType operationType,
            BigDecimal amount,
            String referenceId,
            String metadataJson
    ) {
        String sql = QueryBuilder.insert(
                        "collateral_operations",
                        "collateral_id",
                        "user_id",
                        "device_id",
                        "asset_code",
                        "operation_type",
                        "amount",
                        "status",
                        "reference_id",
                        "metadata"
                )
                .build();

        jdbcClient.sql(sql.replace(":metadata", "CAST(:metadata AS jsonb)"))
                .param("collateralId", collateralId == null || collateralId.isBlank() ? null : UUID.fromString(collateralId))
                .param("userId", userId)
                .param("deviceId", deviceId)
                .param("assetCode", assetCode)
                .param("operationType", operationType.name())
                .param("amount", amount)
                .param("status", CollateralOperationStatus.REQUESTED.name())
                .param("referenceId", referenceId)
                .param("metadata", metadataJson)
                .update();

        return findByReferenceId(referenceId).orElseThrow();
    }

    @Override
    public void markCompleted(String referenceId, String collateralId, String metadataJson) {
        String sql = QueryBuilder.update("collateral_operations")
                .set("status = :status")
                .set("collateral_id = COALESCE(:collateralId, collateral_id)")
                .set("error_message = NULL")
                .set("metadata = metadata || CAST(:metadata AS jsonb)")
                .touchUpdatedAt()
                .where("reference_id", QueryBuilder.Op.EQ, ":referenceId")
                .build();
        jdbcClient.sql(sql)
                .param("status", CollateralOperationStatus.COMPLETED.name())
                .param("collateralId", collateralId == null || collateralId.isBlank() ? null : UUID.fromString(collateralId))
                .param("metadata", metadataJson)
                .param("referenceId", referenceId)
                .update();
    }

    @Override
    public void markFailed(String referenceId, String errorMessage, String metadataJson) {
        String sql = QueryBuilder.update("collateral_operations")
                .set("status = :status")
                .set("error_message = :errorMessage")
                .set("metadata = metadata || CAST(:metadata AS jsonb)")
                .touchUpdatedAt()
                .where("reference_id", QueryBuilder.Op.EQ, ":referenceId")
                .build();
        jdbcClient.sql(sql)
                .param("status", CollateralOperationStatus.FAILED.name())
                .param("errorMessage", errorMessage)
                .param("metadata", metadataJson)
                .param("referenceId", referenceId)
                .update();
    }

    @Override
    public Optional<CollateralOperation> findByReferenceId(String referenceId) {
        String sql = QueryBuilder.select("collateral_operations")
                .where("reference_id", QueryBuilder.Op.EQ, ":referenceId")
                .limit(1)
                .build();
        return jdbcClient.sql(sql)
                .param("referenceId", referenceId)
                .query(rowMapper)
                .optional();
    }

    @Override
    public List<CollateralOperation> findRecent(
            int size,
            CollateralOperationType operationType,
            CollateralOperationStatus status,
            String assetCode
    ) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("collateral_operations")
                .orderBy("created_at DESC")
                .limit(size);
        if (operationType != null) {
            builder.where("operation_type", QueryBuilder.Op.EQ, ":operationType");
        }
        if (status != null) {
            builder.where("status", QueryBuilder.Op.EQ, ":status");
        }
        if (assetCode != null && !assetCode.isBlank()) {
            builder.where("asset_code", QueryBuilder.Op.EQ, ":assetCode");
        }
        JdbcClient.StatementSpec spec = jdbcClient.sql(builder.build());
        if (operationType != null) {
            spec.param("operationType", operationType.name());
        }
        if (status != null) {
            spec.param("status", status.name());
        }
        if (assetCode != null && !assetCode.isBlank()) {
            spec.param("assetCode", assetCode.toUpperCase());
        }
        return spec.query(rowMapper).list();
    }
}
