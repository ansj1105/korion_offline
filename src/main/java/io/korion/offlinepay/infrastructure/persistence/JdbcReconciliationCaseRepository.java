package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import io.korion.offlinepay.infrastructure.persistence.QueryBuilder.Op;
import io.korion.offlinepay.infrastructure.persistence.mapper.ReconciliationCaseRowMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReconciliationCaseRepository implements ReconciliationCaseRepository {

    private final JdbcClient jdbcClient;
    private final ReconciliationCaseRowMapper rowMapper;

    public JdbcReconciliationCaseRepository(JdbcClient jdbcClient, ReconciliationCaseRowMapper rowMapper) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public ReconciliationCase save(
            String settlementId,
            String batchId,
            String proofId,
            String voucherId,
            String caseType,
            ReconciliationCaseStatus status,
            String reasonCode,
            String detailJson
    ) {
        requireNonBlank(batchId, "batchId");
        requireNonBlank(caseType, "caseType");
        requireNonNull(status, "status");
        requireNonBlank(detailJson, "detailJson");
        String normalizedReasonCode = requireReasonCode(reasonCode, "reconciliation case");
        String sql = QueryBuilder.insert(
                        "reconciliation_cases",
                        "settlement_id",
                        "batch_id",
                        "proof_id",
                        "voucher_id",
                        "case_type",
                        "status",
                        "reason_code",
                        "detail"
                )
                .value("detail", "CAST(:detail AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("settlementId", settlementId == null ? null : java.util.UUID.fromString(settlementId))
                .param("batchId", java.util.UUID.fromString(batchId))
                .param("proofId", proofId == null ? null : java.util.UUID.fromString(proofId))
                .param("voucherId", voucherId)
                .param("caseType", caseType)
                .param("status", status.name())
                .param("reasonCode", normalizedReasonCode)
                .param("detail", detailJson)
                .update();

        String selectSql = QueryBuilder.select("reconciliation_cases")
                .where("batch_id", QueryBuilder.Op.EQ, ":batchId")
                .orderBy("created_at DESC")
                .limit(1)
                .build();
        return jdbcClient.sql(selectSql)
                .param("batchId", java.util.UUID.fromString(batchId))
                .query(rowMapper)
                .single();
    }

    @Override
    public List<ReconciliationCase> findRecent(int size, ReconciliationCaseStatus status, String caseType, String reasonCode) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("reconciliation_cases");
        if (status != null) {
            builder.where("status", QueryBuilder.Op.EQ, ":status");
        }
        if (caseType != null && !caseType.isBlank()) {
            builder.where("case_type", QueryBuilder.Op.EQ, ":caseType");
        }
        if (reasonCode != null && !reasonCode.isBlank()) {
            builder.where("reason_code", QueryBuilder.Op.EQ, ":reasonCode");
        }
        String sql = builder.orderBy("updated_at DESC")
                .limit(size)
                .build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        if (status != null) {
            statement.param("status", status.name());
        }
        if (caseType != null && !caseType.isBlank()) {
            statement.param("caseType", caseType);
        }
        if (reasonCode != null && !reasonCode.isBlank()) {
            statement.param("reasonCode", reasonCode);
        }
        return statement.query(rowMapper).list();
    }

    @Override
    public Optional<ReconciliationCase> findOpenBySettlementIdAndCaseType(String settlementId, String caseType) {
        requireNonBlank(settlementId, "settlementId");
        requireNonBlank(caseType, "caseType");
        String sql = QueryBuilder.select("reconciliation_cases")
                .where("settlement_id", Op.EQ, ":settlementId")
                .where("case_type", Op.EQ, ":caseType")
                .where("status", Op.EQ, ":status")
                .orderBy("created_at DESC")
                .limit(1)
                .build();
        List<ReconciliationCase> cases = jdbcClient.sql(sql)
                .param("settlementId", java.util.UUID.fromString(settlementId))
                .param("caseType", caseType)
                .param("status", ReconciliationCaseStatus.OPEN.name())
                .query(rowMapper)
                .list();
        return cases.stream().findFirst();
    }

    @Override
    public void resolve(String caseId, String detailJson) {
        requireNonBlank(caseId, "caseId");
        requireNonBlank(detailJson, "detailJson");
        String sql = QueryBuilder.update("reconciliation_cases")
                .set("status", ":status")
                .set("detail", "CAST(:detail AS jsonb)")
                .set("resolved_at", "NOW()")
                .touchUpdatedAt()
                .where("id", Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("status", ReconciliationCaseStatus.RESOLVED.name())
                .param("detail", detailJson)
                .param("id", java.util.UUID.fromString(caseId))
                .update();
    }

    private String requireReasonCode(String reasonCode, String context) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalStateException("reasonCode is required for " + context);
        }
        return reasonCode;
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
