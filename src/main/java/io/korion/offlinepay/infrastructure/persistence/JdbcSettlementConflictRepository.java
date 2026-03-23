package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.SettlementConflictRepository;
import io.korion.offlinepay.domain.model.SettlementConflict;
import io.korion.offlinepay.domain.model.SettlementConflictMetric;
import io.korion.offlinepay.infrastructure.persistence.mapper.SettlementConflictMetricRowMapper;
import io.korion.offlinepay.infrastructure.persistence.mapper.SettlementConflictRowMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSettlementConflictRepository implements SettlementConflictRepository {

    private final JdbcClient jdbcClient;
    private final SettlementConflictRowMapper settlementConflictRowMapper;
    private final SettlementConflictMetricRowMapper settlementConflictMetricRowMapper;

    public JdbcSettlementConflictRepository(
            JdbcClient jdbcClient,
            SettlementConflictRowMapper settlementConflictRowMapper,
            SettlementConflictMetricRowMapper settlementConflictMetricRowMapper
    ) {
        this.jdbcClient = jdbcClient;
        this.settlementConflictRowMapper = settlementConflictRowMapper;
        this.settlementConflictMetricRowMapper = settlementConflictMetricRowMapper;
    }

    @Override
    public SettlementConflict save(
            String settlementId,
            String voucherId,
            String collateralId,
            String deviceId,
            String conflictType,
            String severity,
            String detailJson
    ) {
        String sql = QueryBuilder.insert(
                        "settlement_conflicts",
                        "settlement_id",
                        "voucher_id",
                        "collateral_id",
                        "device_id",
                        "conflict_type",
                        "severity",
                        "status",
                        "detail"
                )
                .value("status", "'OPEN'")
                .value("detail", "CAST(:detail AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("settlementId", java.util.UUID.fromString(settlementId))
                .param("voucherId", voucherId)
                .param("collateralId", java.util.UUID.fromString(collateralId))
                .param("deviceId", deviceId)
                .param("conflictType", conflictType)
                .param("severity", severity)
                .param("detail", detailJson)
                .update();

        String selectSql = QueryBuilder.select("settlement_conflicts")
                .where("settlement_id", QueryBuilder.Op.EQ, ":settlementId")
                .orderBy("created_at DESC")
                .limit(1)
                .build();
        return jdbcClient.sql(selectSql)
                .param("settlementId", java.util.UUID.fromString(settlementId))
                .query(settlementConflictRowMapper)
                .single();
    }

    @Override
    public List<SettlementConflict> findRecent(
            String status,
            String conflictType,
            String collateralId,
            String deviceId,
            String networkScope,
            int size
    ) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("settlement_conflicts");
        if (status != null && !status.isBlank()) {
            builder.where("status", QueryBuilder.Op.EQ, ":status");
        }
        if (conflictType != null && !conflictType.isBlank()) {
            builder.where("conflict_type", QueryBuilder.Op.EQ, ":conflictType");
        }
        if (collateralId != null && !collateralId.isBlank()) {
            builder.where("collateral_id", QueryBuilder.Op.EQ, "CAST(:collateralId AS uuid)");
        }
        if (deviceId != null && !deviceId.isBlank()) {
            builder.where("device_id", QueryBuilder.Op.EQ, ":deviceId");
        }
        appendNetworkScopeFilter(builder, networkScope);

        String sql = builder
                .orderBy("updated_at DESC")
                .orderBy("created_at DESC")
                .limit(size)
                .build();

        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        if (status != null && !status.isBlank()) {
            statement.param("status", status);
        }
        if (conflictType != null && !conflictType.isBlank()) {
            statement.param("conflictType", conflictType);
        }
        if (collateralId != null && !collateralId.isBlank()) {
            statement.param("collateralId", collateralId);
        }
        if (deviceId != null && !deviceId.isBlank()) {
            statement.param("deviceId", deviceId);
        }
        bindNetworkScope(statement, networkScope);
        return statement.query(settlementConflictRowMapper).list();
    }

    @Override
    public List<SettlementConflictMetric> summarizeByHour(int hours, String networkScope) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select(
                        "settlement_conflicts",
                        "date_trunc('hour', created_at) AS bucket_at",
                        "COUNT(*) AS count"
                )
                .where("created_at", QueryBuilder.Op.GTE, "NOW() - make_interval(hours => :hours)");
        appendNetworkScopeFilter(builder, networkScope);

        String sql = builder.groupBy("date_trunc('hour', created_at)")
                .orderBy("bucket_at ASC")
                .build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
                .param("hours", hours)
                ;
        bindNetworkScope(statement, networkScope);
        return statement
                .query(settlementConflictMetricRowMapper)
                .list();
    }

    private void appendNetworkScopeFilter(QueryBuilder.SelectBuilder builder, String networkScope) {
        if (networkScope == null || networkScope.isBlank()) {
            return;
        }

        builder.where("""
                EXISTS (
                    SELECT 1
                    FROM settlement_requests settlement_request_filter
                    JOIN offline_payment_proofs offline_payment_proof_filter
                      ON offline_payment_proof_filter.id = settlement_request_filter.proof_id
                    WHERE settlement_request_filter.id = settlement_conflicts.settlement_id
                      AND %s = :networkScope
                )
                """.formatted(networkScopeExpression("offline_payment_proof_filter")));
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
