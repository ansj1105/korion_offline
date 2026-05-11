package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.SettlementResultRepository;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementResultRecord;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.SettlementStatus;
import io.korion.offlinepay.infrastructure.persistence.mapper.SettlementResultRowMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSettlementResultRepository implements SettlementResultRepository {

    private final JdbcClient jdbcClient;
    private final SettlementResultRowMapper settlementResultRowMapper;

    public JdbcSettlementResultRepository(JdbcClient jdbcClient, SettlementResultRowMapper settlementResultRowMapper) {
        this.jdbcClient = jdbcClient;
        this.settlementResultRowMapper = settlementResultRowMapper;
    }

    @Override
    public boolean existsByVoucherId(String voucherId) {
        String sql = QueryBuilder.select("settlements", "COUNT(*)")
                .where("voucher_id", QueryBuilder.Op.EQ, ":voucherId")
                .build();
        Integer count = jdbcClient.sql(sql)
                .param("voucherId", voucherId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public SettlementResultRecord save(
            String settlementId,
            String batchId,
            OfflinePaymentProof proof,
            SettlementStatus status,
            String reasonCode,
            String detailJson,
            BigDecimal settledAmount
    ) {
        String normalizedReasonCode = normalizeReasonCode(status, reasonCode);
        String sql = QueryBuilder.insert(
                        "settlements",
                        "settlement_id",
                        "batch_id",
                        "voucher_id",
                        "collateral_id",
                        "sender_device_id",
                        "receiver_device_id",
                        "status",
                        "reason_code",
                        "detail",
                        "settled_amount"
                )
                .value("detail", "CAST(:detail AS jsonb)")
                .build();
        sql = sql + """
                ON CONFLICT (settlement_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    reason_code = EXCLUDED.reason_code,
                    detail = settlements.detail || EXCLUDED.detail,
                    settled_amount = EXCLUDED.settled_amount,
                    processed_at = NOW()
                """;
        jdbcClient.sql(sql)
                .param("settlement_id", java.util.UUID.fromString(settlementId))
                .param("batch_id", java.util.UUID.fromString(batchId))
                .param("voucher_id", proof.voucherId())
                .param("collateral_id", java.util.UUID.fromString(proof.collateralId()))
                .param("sender_device_id", proof.senderDeviceId())
                .param("receiver_device_id", proof.receiverDeviceId())
                .param("status", status.name())
                .param("reason_code", normalizedReasonCode)
                .param("detail", detailJson)
                .param("settled_amount", settledAmount)
                .update();

        String selectSql = QueryBuilder.select("settlements")
                .where("settlement_id", QueryBuilder.Op.EQ, ":settlementId")
                .build();
        return jdbcClient.sql(selectSql)
                .param("settlementId", java.util.UUID.fromString(settlementId))
                .query(settlementResultRowMapper)
                .single();
    }

    private String normalizeReasonCode(SettlementStatus status, String reasonCode) {
        return switch (status) {
            case SETTLED -> reasonCode == null || reasonCode.isBlank() ? OfflinePayReasonCode.SETTLED : reasonCode;
            case REJECTED, CONFLICT, EXPIRED -> requireReasonCode(reasonCode, "settlement result terminal status");
            case PENDING, VALIDATING -> reasonCode;
        };
    }

    private String requireReasonCode(String reasonCode, String context) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalStateException("reasonCode is required for " + context);
        }
        return reasonCode;
    }

    @Override
    public List<SettlementResultRecord> findByBatchId(String batchId) {
        String sql = QueryBuilder.select("settlements")
                .where("batch_id", QueryBuilder.Op.EQ, ":batchId")
                .orderBy("created_at ASC")
                .build();
        return jdbcClient.sql(sql)
                .param("batchId", java.util.UUID.fromString(batchId))
                .query(settlementResultRowMapper)
                .list();
    }
}
