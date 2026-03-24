package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.SettlementRepository;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.SettlementStatus;
import io.korion.offlinepay.infrastructure.persistence.mapper.SettlementRequestRowMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSettlementRepository implements SettlementRepository {

    private final JdbcClient jdbcClient;
    private final SettlementRequestRowMapper settlementRequestRowMapper;

    public JdbcSettlementRepository(JdbcClient jdbcClient, SettlementRequestRowMapper settlementRequestRowMapper) {
        this.jdbcClient = jdbcClient;
        this.settlementRequestRowMapper = settlementRequestRowMapper;
    }

    @Override
    public SettlementRequest save(String batchId, String collateralId, String proofId, SettlementStatus status, String reasonCode, boolean conflictDetected, String settlementResultJson) {
        String normalizedReasonCode = normalizeReasonCode(status, reasonCode, conflictDetected);
        String sql = QueryBuilder
                .insert("settlement_requests", "batch_id", "collateral_id", "proof_id", "status", "reason_code", "conflict_detected", "settlement_result")
                .value("settlement_result", "CAST(:settlementResult AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("batch_id", java.util.UUID.fromString(batchId))
                .param("collateral_id", java.util.UUID.fromString(collateralId))
                .param("proof_id", java.util.UUID.fromString(proofId))
                .param("status", status.name())
                .param("reason_code", normalizedReasonCode)
                .param("conflict_detected", conflictDetected)
                .param("settlementResult", settlementResultJson)
                .update();

        String selectSql = QueryBuilder.select("settlement_requests")
                .where("batch_id", QueryBuilder.Op.EQ, ":batchId")
                .orderBy("created_at DESC")
                .limit(1)
                .build();
        return jdbcClient.sql(selectSql)
                .param("batchId", java.util.UUID.fromString(batchId))
                .query(settlementRequestRowMapper)
                .single();
    }

    @Override
    public List<SettlementRequest> findByBatchId(String batchId) {
        String sql = QueryBuilder.select("settlement_requests")
                .where("batch_id", QueryBuilder.Op.EQ, ":batchId")
                .orderBy("created_at ASC")
                .build();
        return jdbcClient.sql(sql)
                .param("batchId", java.util.UUID.fromString(batchId))
                .query(settlementRequestRowMapper)
                .list();
    }

    @Override
    public Optional<SettlementRequest> findById(String settlementId) {
        String sql = QueryBuilder.select("settlement_requests")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(settlementId))
                .query(settlementRequestRowMapper)
                .optional();
    }

    @Override
    public void update(String settlementId, SettlementStatus status, String reasonCode, boolean conflictDetected, String settlementResultJson) {
        String normalizedReasonCode = normalizeReasonCode(status, reasonCode, conflictDetected);
        String sql = QueryBuilder.update("settlement_requests")
                .set("status", ":status")
                .set("reason_code", ":reasonCode")
                .set("conflict_detected", ":conflictDetected")
                .set("settlement_result", "settlement_result || CAST(:settlementResult AS jsonb)")
                .touchUpdatedAt()
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(settlementId))
                .param("status", status.name())
                .param("reasonCode", normalizedReasonCode)
                .param("conflictDetected", conflictDetected)
                .param("settlementResult", settlementResultJson)
                .update();
    }

    private String normalizeReasonCode(SettlementStatus status, String reasonCode, boolean conflictDetected) {
        if (conflictDetected || status == SettlementStatus.CONFLICT) {
            return requireReasonCode(reasonCode, "settlement conflict");
        }
        return switch (status) {
            case SETTLED -> reasonCode == null || reasonCode.isBlank() ? OfflinePayReasonCode.SETTLED : reasonCode;
            case REJECTED, EXPIRED -> requireReasonCode(reasonCode, "settlement terminal status");
            case PENDING, VALIDATING -> reasonCode;
            case CONFLICT -> requireReasonCode(reasonCode, "settlement terminal status");
        };
    }

    private String requireReasonCode(String reasonCode, String context) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalStateException("reasonCode is required for " + context);
        }
        return reasonCode;
    }
}
