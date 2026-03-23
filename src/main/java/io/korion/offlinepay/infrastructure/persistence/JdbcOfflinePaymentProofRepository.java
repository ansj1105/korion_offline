package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.infrastructure.persistence.mapper.OfflinePaymentProofRowMapper;
import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOfflinePaymentProofRepository implements OfflinePaymentProofRepository {

    private final JdbcClient jdbcClient;
    private final OfflinePaymentProofRowMapper offlinePaymentProofRowMapper;

    public JdbcOfflinePaymentProofRepository(JdbcClient jdbcClient, OfflinePaymentProofRowMapper offlinePaymentProofRowMapper) {
        this.jdbcClient = jdbcClient;
        this.offlinePaymentProofRowMapper = offlinePaymentProofRowMapper;
    }

    @Override
    public OfflinePaymentProof save(
            String batchId,
            String voucherId,
            String collateralId,
            String senderDeviceId,
            String receiverDeviceId,
            int keyVersion,
            int policyVersion,
            long counter,
            String nonce,
            String hashChainHead,
            String previousHash,
            String signature,
            BigDecimal amount,
            long timestampMs,
            long expiresAtMs,
            String canonicalPayload,
            String uploaderType,
            String channelType,
            String rawPayloadJson
    ) {
        String sql = QueryBuilder
                .insert(
                        "offline_payment_proofs",
                        "batch_id",
                        "voucher_id",
                        "collateral_id",
                        "sender_device_id",
                        "receiver_device_id",
                        "key_version",
                        "policy_version",
                        "counter",
                        "nonce",
                        "hash_chain_head",
                        "previous_hash",
                        "signature",
                        "amount",
                        "timestamp_ms",
                        "expires_at_ms",
                        "canonical_payload",
                        "uploader_type",
                        "channel_type",
                        "status",
                        "raw_payload"
                )
                .value("raw_payload", "CAST(:rawPayload AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("batchId", java.util.UUID.fromString(batchId))
                .param("voucherId", voucherId)
                .param("collateralId", java.util.UUID.fromString(collateralId))
                .param("senderDeviceId", senderDeviceId)
                .param("receiverDeviceId", receiverDeviceId)
                .param("keyVersion", keyVersion)
                .param("policyVersion", policyVersion)
                .param("counter", counter)
                .param("nonce", nonce)
                .param("hashChainHead", hashChainHead)
                .param("previousHash", previousHash)
                .param("signature", signature)
                .param("amount", amount)
                .param("timestampMs", timestampMs)
                .param("expiresAtMs", expiresAtMs)
                .param("canonicalPayload", canonicalPayload)
                .param("uploaderType", uploaderType)
                .param("channelType", channelType == null || channelType.isBlank() ? "UNKNOWN" : channelType)
                .param("status", OfflineProofStatus.UPLOADED.name())
                .param("rawPayload", rawPayloadJson)
                .update();

        return findByVoucherId(voucherId).orElseThrow();
    }

    @Override
    public void updateLifecycle(String proofId, OfflineProofStatus status, String reasonCode, boolean verified, boolean settled) {
        String sql = QueryBuilder.update("offline_payment_proofs")
                .set("status", ":status")
                .set("reason_code", ":reasonCode")
                .set("verified_at", "CASE WHEN :verified THEN COALESCE(verified_at, NOW()) ELSE verified_at END")
                .set("settled_at", "CASE WHEN :settled THEN COALESCE(settled_at, NOW()) ELSE settled_at END")
                .touchUpdatedAt()
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(proofId))
                .param("status", status.name())
                .param("reasonCode", reasonCode)
                .param("verified", verified)
                .param("settled", settled)
                .update();
    }

    @Override
    public java.util.Optional<OfflinePaymentProof> findById(String proofId) {
        String sql = QueryBuilder.select("offline_payment_proofs")
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(proofId))
                .query(offlinePaymentProofRowMapper)
                .optional();
    }

    @Override
    public java.util.Optional<OfflinePaymentProof> findByVoucherId(String voucherId) {
        String sql = QueryBuilder.select("offline_payment_proofs")
                .where("voucher_id", QueryBuilder.Op.EQ, ":voucherId")
                .build();
        return jdbcClient.sql(sql)
                .param("voucherId", voucherId)
                .query(offlinePaymentProofRowMapper)
                .optional();
    }

    @Override
    public java.util.Optional<OfflinePaymentProof> findBySenderNonce(String senderDeviceId, String nonce) {
        String sql = QueryBuilder.select("offline_payment_proofs")
                .where("sender_device_id", QueryBuilder.Op.EQ, ":senderDeviceId")
                .where("nonce", QueryBuilder.Op.EQ, ":nonce")
                .build();
        return jdbcClient.sql(sql)
                .param("senderDeviceId", senderDeviceId)
                .param("nonce", nonce)
                .query(offlinePaymentProofRowMapper)
                .optional();
    }

    @Override
    public java.util.List<OfflinePaymentProof> findByCollateralId(String collateralId) {
        String sql = QueryBuilder.select("offline_payment_proofs")
                .where("collateral_id", QueryBuilder.Op.EQ, ":collateralId")
                .orderBy("counter ASC")
                .orderBy("created_at ASC")
                .build();
        return jdbcClient.sql(sql)
                .param("collateralId", java.util.UUID.fromString(collateralId))
                .query(offlinePaymentProofRowMapper)
                .list();
    }

    @Override
    public java.util.List<OfflinePaymentProof> findRecent(int size, OfflineProofStatus status, String channelType) {
        QueryBuilder.SelectBuilder builder = QueryBuilder.select("offline_payment_proofs");
        if (status != null) {
            builder.where("status", QueryBuilder.Op.EQ, ":status");
        }
        if (channelType != null && !channelType.isBlank()) {
            builder.where("channel_type", QueryBuilder.Op.EQ, ":channelType");
        }
        String sql = builder
                .orderBy("created_at DESC")
                .limit(size)
                .build();
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
                ;
        if (status != null) {
            statement.param("status", status.name());
        }
        if (channelType != null && !channelType.isBlank()) {
            statement.param("channelType", channelType.trim().toUpperCase());
        }
        return statement.query(offlinePaymentProofRowMapper).list();
    }
}
