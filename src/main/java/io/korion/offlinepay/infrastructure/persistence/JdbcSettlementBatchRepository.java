package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.SettlementBatchRepository;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementStatusMetric;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import io.korion.offlinepay.infrastructure.persistence.mapper.SettlementBatchRowMapper;
import io.korion.offlinepay.infrastructure.persistence.mapper.SettlementStatusMetricRowMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSettlementBatchRepository implements SettlementBatchRepository {

    private final JdbcClient jdbcClient;
    private final SettlementBatchRowMapper settlementBatchRowMapper;
    private final SettlementStatusMetricRowMapper settlementStatusMetricRowMapper;

    public JdbcSettlementBatchRepository(
            JdbcClient jdbcClient,
            SettlementBatchRowMapper settlementBatchRowMapper,
            SettlementStatusMetricRowMapper settlementStatusMetricRowMapper
    ) {
        this.jdbcClient = jdbcClient;
        this.settlementBatchRowMapper = settlementBatchRowMapper;
        this.settlementStatusMetricRowMapper = settlementStatusMetricRowMapper;
    }

    @Override
    public SettlementBatch save(String sourceDeviceId, String idempotencyKey, SettlementBatchStatus status, String lastReasonCode, int proofsCount, String summaryJson) {
        String normalizedReasonCode = normalizeReasonCode(status, lastReasonCode);
        String sql = QueryBuilder
                .insert("settlement_batches", "source_device_id", "idempotency_key", "status", "last_reason_code", "proofs_count", "summary")
                .value("summary", "CAST(:summary AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("source_device_id", sourceDeviceId)
                .param("idempotency_key", idempotencyKey)
                .param("status", status.name())
                .param("last_reason_code", normalizedReasonCode)
                .param("proofs_count", proofsCount)
                .param("summary", summaryJson)
                .update();

        return findByIdempotencyKey(idempotencyKey).orElseThrow();
    }

    @Override
    public Optional<SettlementBatch> findById(String batchId) {
        String sql = QueryBuilder.select("settlement_batches")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(batchId))
                .query(settlementBatchRowMapper)
                .optional();
    }

    @Override
    public Optional<SettlementBatch> findByIdempotencyKey(String idempotencyKey) {
        String sql = QueryBuilder.select("settlement_batches")
                .where("idempotency_key", QueryBuilder.Op.EQ, ":idempotencyKey")
                .build();
        return jdbcClient.sql(sql)
                .param("idempotencyKey", idempotencyKey)
                .query(settlementBatchRowMapper)
                .optional();
    }

    @Override
    public void updateStatus(String batchId, SettlementBatchStatus status, String lastReasonCode, String summaryJson) {
        String normalizedReasonCode = normalizeReasonCode(status, lastReasonCode);
        String sql = QueryBuilder.update("settlement_batches")
                .set("status", ":status")
                .set("last_reason_code", ":lastReasonCode")
                .set("summary", "summary || CAST(:summary AS jsonb)")
                .touchUpdatedAt()
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(batchId))
                .param("status", status.name())
                .param("lastReasonCode", normalizedReasonCode)
                .param("summary", summaryJson)
                .update();
    }

    private String normalizeReasonCode(SettlementBatchStatus status, String reasonCode) {
        return switch (status) {
            case SETTLED -> reasonCode == null || reasonCode.isBlank() ? OfflinePayReasonCode.SETTLED : reasonCode;
            case FAILED, PARTIALLY_SETTLED, CLOSED -> requireReasonCode(reasonCode, "settlement batch terminal status");
            case CREATED, UPLOADED, VALIDATING -> reasonCode;
        };
    }

    private String requireReasonCode(String reasonCode, String context) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalStateException("reasonCode is required for " + context);
        }
        return reasonCode;
    }

    @Override
    public List<SettlementBatch> findPendingValidationBatches(int limit) {
        String sql = QueryBuilder.select("settlement_batches")
                .where("status IN ('CREATED', 'UPLOADED')")
                .orderBy("created_at ASC")
                .limit(limit)
                .build();
        return jdbcClient.sql(sql)
                .query(settlementBatchRowMapper)
                .list();
    }

    @Override
    public List<SettlementBatch> findRecentConflictedBatches(int limit) {
        String sql = QueryBuilder.select("settlement_batches")
                .where("status IN ('PARTIALLY_SETTLED', 'FAILED')")
                .orderBy("updated_at DESC")
                .limit(limit)
                .build();
        return jdbcClient.sql(sql)
                .query(settlementBatchRowMapper)
                .list();
    }

    @Override
    public List<SettlementBatch> findDeadLetterBatches(int limit, String networkScope) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("settlement_batches")
                .where("status", QueryBuilder.Op.EQ, "'FAILED'")
                .where("jsonb_exists(summary, 'deadLetteredAt')");
        appendNetworkScopeFilter(builder, "settlement_batches.id", networkScope);

        String sql = builder.orderBy("updated_at DESC")
                .limit(limit)
                .build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        bindNetworkScope(statement, networkScope);
        return statement
                .query(settlementBatchRowMapper)
                .list();
    }

    @Override
    public List<SettlementStatusMetric> summarizeStatusByHour(int hours, String networkScope) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select(
                        "settlement_batches",
                        "date_trunc('hour', updated_at) AS bucket_at",
                        "status",
                        "COUNT(*) AS count"
                )
                .where("updated_at", QueryBuilder.Op.GTE, "NOW() - make_interval(hours => :hours)");
        appendNetworkScopeFilter(builder, "settlement_batches.id", networkScope);

        String sql = builder.groupBy("date_trunc('hour', updated_at)")
                .groupBy("status")
                .orderBy("bucket_at ASC")
                .orderBy("status ASC")
                .build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
                .param("hours", hours)
                ;
        bindNetworkScope(statement, networkScope);
        return statement
                .query(settlementStatusMetricRowMapper)
                .list();
    }

    @Override
    public List<SettlementBatch> findRecentBatches(int limit, String networkScope) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("settlement_batches");
        appendNetworkScopeFilter(builder, "settlement_batches.id", networkScope);

        String sql = builder
                .orderBy("updated_at DESC")
                .limit(limit)
                .build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        bindNetworkScope(statement, networkScope);
        return statement
                .query(settlementBatchRowMapper)
                .list();
    }

    @Override
    public long countDeadLetterBatches(int hours, String networkScope) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("settlement_batches", "COUNT(*) AS count")
                .where("status", QueryBuilder.Op.EQ, "'FAILED'")
                .where("jsonb_exists(summary, 'deadLetteredAt')")
                .where("updated_at", QueryBuilder.Op.GTE, "NOW() - make_interval(hours => :hours)");
        appendNetworkScopeFilter(builder, "settlement_batches.id", networkScope);

        String sql = builder.build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
                .param("hours", hours)
                ;
        bindNetworkScope(statement, networkScope);
        Long count = statement
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    private void appendNetworkScopeFilter(QueryBuilder.SelectBuilder builder, String batchIdExpression, String networkScope) {
        if (networkScope == null || networkScope.isBlank()) {
            return;
        }

        builder.where("""
                EXISTS (
                    SELECT 1
                    FROM settlement_requests settlement_request_filter
                    JOIN offline_payment_proofs offline_payment_proof_filter
                      ON offline_payment_proof_filter.id = settlement_request_filter.proof_id
                    WHERE settlement_request_filter.batch_id = %s
                      AND %s = :networkScope
                )
                """.formatted(batchIdExpression, networkScopeExpression("offline_payment_proof_filter")));
    }

    private String networkScopeExpression(String proofAlias) {
        return """
                COALESCE(
                    NULLIF(CAST(%s.raw_payload AS jsonb) ->> 'networkMode', ''),
                    CASE
                        WHEN CAST(%s.raw_payload AS jsonb) ->> 'network' = 'mainnet' THEN 'mainnet'
                        ELSE 'testnet'
                    END
                )
                """.formatted(proofAlias, proofAlias);
    }

    private void bindNetworkScope(JdbcClient.StatementSpec statement, String networkScope) {
        if (networkScope != null && !networkScope.isBlank()) {
            statement.param("networkScope", networkScope);
        }
    }
}
