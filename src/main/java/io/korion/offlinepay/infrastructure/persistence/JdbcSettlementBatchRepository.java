package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.SettlementBatchRepository;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementStatusMetric;
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
    public SettlementBatch save(String sourceDeviceId, String idempotencyKey, SettlementBatchStatus status, int proofsCount, String summaryJson) {
        String sql = QueryBuilder
                .insert("settlement_batches", "source_device_id", "idempotency_key", "status", "proofs_count", "summary")
                .build();
        jdbcClient.sql(sql.replace(":summary", "CAST(:summary AS jsonb)"))
                .param("sourceDeviceId", sourceDeviceId)
                .param("idempotencyKey", idempotencyKey)
                .param("status", status.name())
                .param("proofsCount", proofsCount)
                .param("summary", summaryJson)
                .update();

        return findByIdempotencyKey(idempotencyKey).orElseThrow();
    }

    @Override
    public Optional<SettlementBatch> findById(String batchId) {
        String sql = QueryBuilder.select("settlement_batches")
                .where("id = :id")
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(batchId))
                .query(settlementBatchRowMapper)
                .optional();
    }

    @Override
    public Optional<SettlementBatch> findByIdempotencyKey(String idempotencyKey) {
        String sql = QueryBuilder.select("settlement_batches")
                .where("idempotency_key = :idempotencyKey")
                .build();
        return jdbcClient.sql(sql)
                .param("idempotencyKey", idempotencyKey)
                .query(settlementBatchRowMapper)
                .optional();
    }

    @Override
    public void updateStatus(String batchId, SettlementBatchStatus status, String summaryJson) {
        String sql = QueryBuilder.update("settlement_batches")
                .set("status = :status")
                .set("summary = summary || CAST(:summary AS jsonb)")
                .touchUpdatedAt()
                .where("id = :id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(batchId))
                .param("status", status.name())
                .param("summary", summaryJson)
                .update();
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
    public List<SettlementBatch> findDeadLetterBatches(int limit) {
        String sql = QueryBuilder.select("settlement_batches")
                .where("status", QueryBuilder.Op.EQ, "'FAILED'")
                .where("summary ? 'deadLetteredAt'")
                .orderBy("updated_at DESC")
                .limit(limit)
                .build();
        return jdbcClient.sql(sql)
                .query(settlementBatchRowMapper)
                .list();
    }

    @Override
    public List<SettlementStatusMetric> summarizeStatusByHour(int hours) {
        String sql = QueryBuilder.select(
                        "settlement_batches",
                        "date_trunc('hour', updated_at) AS bucket_at",
                        "status",
                        "COUNT(*) AS count"
                )
                .where("updated_at", QueryBuilder.Op.GTE, "NOW() - make_interval(hours => :hours)")
                .groupBy("date_trunc('hour', updated_at)")
                .groupBy("status")
                .orderBy("bucket_at ASC")
                .orderBy("status ASC")
                .build();
        return jdbcClient.sql(sql)
                .param("hours", hours)
                .query(settlementStatusMetricRowMapper)
                .list();
    }

    @Override
    public List<SettlementBatch> findRecentBatches(int limit) {
        String sql = QueryBuilder.select("settlement_batches")
                .orderBy("updated_at DESC")
                .limit(limit)
                .build();
        return jdbcClient.sql(sql)
                .query(settlementBatchRowMapper)
                .list();
    }

    @Override
    public long countDeadLetterBatches(int hours) {
        String sql = QueryBuilder.select("settlement_batches", "COUNT(*) AS count")
                .where("status", QueryBuilder.Op.EQ, "'FAILED'")
                .where("summary ? 'deadLetteredAt'")
                .where("updated_at", QueryBuilder.Op.GTE, "NOW() - make_interval(hours => :hours)")
                .build();
        Long count = jdbcClient.sql(sql)
                .param("hours", hours)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }
}
