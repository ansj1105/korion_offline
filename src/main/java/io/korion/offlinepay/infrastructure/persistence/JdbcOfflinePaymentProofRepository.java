package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
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
                        "raw_payload"
                )
                .build();
        jdbcClient.sql(sql.replace(":raw_payload", "CAST(:rawPayload AS jsonb)"))
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
                .param("rawPayload", rawPayloadJson)
                .update();

        return findByVoucherId(voucherId).orElseThrow();
    }

    @Override
    public java.util.Optional<OfflinePaymentProof> findById(String proofId) {
        String sql = QueryBuilder.select("offline_payment_proofs")
                .where("id = :id")
                .build();
        return jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(proofId))
                .query(offlinePaymentProofRowMapper)
                .optional();
    }

    @Override
    public java.util.Optional<OfflinePaymentProof> findByVoucherId(String voucherId) {
        String sql = QueryBuilder.select("offline_payment_proofs")
                .where("voucher_id = :voucherId")
                .build();
        return jdbcClient.sql(sql)
                .param("voucherId", voucherId)
                .query(offlinePaymentProofRowMapper)
                .optional();
    }

    @Override
    public java.util.Optional<OfflinePaymentProof> findBySenderNonce(String senderDeviceId, String nonce) {
        String sql = QueryBuilder.select("offline_payment_proofs")
                .where("sender_device_id = :senderDeviceId")
                .where("nonce = :nonce")
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
                .where("collateral_id = :collateralId")
                .orderBy("counter ASC")
                .orderBy("created_at ASC")
                .build();
        return jdbcClient.sql(sql)
                .param("collateralId", java.util.UUID.fromString(collateralId))
                .query(offlinePaymentProofRowMapper)
                .list();
    }
}
