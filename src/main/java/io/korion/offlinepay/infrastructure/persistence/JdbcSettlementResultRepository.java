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
        jdbcClient.sql(sql)
                .param("settlementId", java.util.UUID.fromString(settlementId))
                .param("batchId", java.util.UUID.fromString(batchId))
                .param("voucherId", proof.voucherId())
                .param("collateralId", java.util.UUID.fromString(proof.collateralId()))
                .param("senderDeviceId", proof.senderDeviceId())
                .param("receiverDeviceId", proof.receiverDeviceId())
                .param("status", status.name())
                .param("reasonCode", normalizedReasonCode)
                .param("detail", detailJson)
                .param("settledAmount", settledAmount)
                .update();

        return findByBatchId(batchId).stream()
                .filter(item -> item.voucherId().equals(proof.voucherId()))
                .findFirst()
                .orElseThrow();
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
