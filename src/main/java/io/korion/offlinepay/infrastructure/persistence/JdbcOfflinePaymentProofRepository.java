package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
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
        requireNonBlank(batchId, "batchId");
        requireNonBlank(voucherId, "voucherId");
        requireNonBlank(collateralId, "collateralId");
        requireNonBlank(senderDeviceId, "senderDeviceId");
        requireNonBlank(receiverDeviceId, "receiverDeviceId");
        requireNonBlank(nonce, "nonce");
        requireNonBlank(hashChainHead, "hashChainHead");
        requireNonBlank(signature, "signature");
        requirePositive(amount, "amount");
        requirePositive(timestampMs, "timestampMs");
        requireExpiresAfterTimestamp(timestampMs, expiresAtMs);
        requireNonBlank(canonicalPayload, "canonicalPayload");
        requireNonBlank(uploaderType, "uploaderType");
        requireNonBlank(rawPayloadJson, "rawPayloadJson");
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
                        "issued_at",
                        "raw_payload"
                )
                .value("raw_payload", "CAST(:rawPayload AS jsonb)")
                .build();
        jdbcClient.sql(sql)
                .param("batch_id", java.util.UUID.fromString(batchId))
                .param("voucher_id", voucherId)
                .param("collateral_id", java.util.UUID.fromString(collateralId))
                .param("sender_device_id", senderDeviceId)
                .param("receiver_device_id", receiverDeviceId)
                .param("key_version", keyVersion)
                .param("policy_version", policyVersion)
                .param("counter", counter)
                .param("nonce", nonce)
                .param("hash_chain_head", hashChainHead)
                .param("previous_hash", previousHash)
                .param("signature", signature)
                .param("amount", amount)
                .param("timestamp_ms", timestampMs)
                .param("expires_at_ms", expiresAtMs)
                .param("canonical_payload", canonicalPayload)
                .param("uploader_type", uploaderType)
                .param("channel_type", channelType == null || channelType.isBlank() ? "UNKNOWN" : channelType)
                .param("status", OfflineProofStatus.UPLOADED.name())
                .param("issued_at", java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestampMs), java.time.ZoneOffset.UTC))
                .param("rawPayload", rawPayloadJson)
                .update();

        return findByVoucherId(voucherId).orElseThrow();
    }

    @Override
    public void updateLifecycle(String proofId, OfflineProofStatus status, String reasonCode, boolean consumed, boolean verified, boolean settled) {
        String normalizedReasonCode = normalizeReasonCode(status, reasonCode);
        String sql = QueryBuilder.update("offline_payment_proofs")
                .set("status", ":status")
                .set("reason_code", ":reasonCode")
                .set("consumed_at", "CASE WHEN :consumed THEN COALESCE(consumed_at, NOW()) ELSE consumed_at END")
                .set("verified_at", "CASE WHEN :verified THEN COALESCE(verified_at, NOW()) ELSE verified_at END")
                .set("settled_at", "CASE WHEN :settled THEN COALESCE(settled_at, NOW()) ELSE settled_at END")
                .touchUpdatedAt()
                .where("id", QueryBuilder.Op.EQ, ":id")
                .build();
        jdbcClient.sql(sql)
                .param("id", java.util.UUID.fromString(proofId))
                .param("status", status.name())
                .param("reasonCode", normalizedReasonCode)
                .param("consumed", consumed)
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
    public java.util.Optional<OfflinePaymentProof> findBySenderRequestId(String senderDeviceId, String requestId) {
        String sql = QueryBuilder.select("offline_payment_proofs")
                .where("sender_device_id", QueryBuilder.Op.EQ, ":senderDeviceId")
                .where("raw_payload ->> 'requestId'", QueryBuilder.Op.EQ, ":requestId")
                .build();
        return jdbcClient.sql(sql)
                .param("senderDeviceId", senderDeviceId)
                .param("requestId", requestId)
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

    @Override
    public java.util.List<OfflinePaymentProof> findRecentByUserIdAndAssetCode(long userId, String assetCode, int size) {
        String sql = """
                SELECT offline_payment_proofs.*
                FROM offline_payment_proofs
                JOIN collateral_locks ON collateral_locks.id = offline_payment_proofs.collateral_id
                WHERE UPPER(collateral_locks.asset_code) = :assetCode
                  AND (
                    EXISTS (
                        SELECT 1
                        FROM devices sender_device
                        WHERE sender_device.user_id = :userId
                          AND sender_device.status = 'ACTIVE'
                          AND sender_device.device_id = offline_payment_proofs.sender_device_id
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM devices receiver_device
                        WHERE receiver_device.user_id = :userId
                          AND receiver_device.status = 'ACTIVE'
                          AND receiver_device.device_id = offline_payment_proofs.receiver_device_id
                    )
                  )
                ORDER BY COALESCE(offline_payment_proofs.settled_at, offline_payment_proofs.updated_at, offline_payment_proofs.created_at) DESC,
                         offline_payment_proofs.created_at DESC
                LIMIT :size
                """;
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("assetCode", assetCode == null || assetCode.isBlank() ? "KORI" : assetCode.trim().toUpperCase())
                .param("size", size)
                .query(offlinePaymentProofRowMapper)
                .list();
    }

    private String normalizeReasonCode(OfflineProofStatus status, String reasonCode) {
        return switch (status) {
            case SETTLED -> reasonCode == null || reasonCode.isBlank() ? OfflinePayReasonCode.SETTLED : reasonCode;
            case REJECTED, CONFLICTED, EXPIRED, FAILED -> requireReasonCode(reasonCode, "offline proof terminal status");
            case ISSUED, UPLOADED, VALIDATING, VERIFIED_OFFLINE, CONSUMED_PENDING_SETTLEMENT -> reasonCode;
        };
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

    private void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void requireExpiresAfterTimestamp(long timestampMs, long expiresAtMs) {
        if (expiresAtMs <= timestampMs) {
            throw new IllegalArgumentException("expiresAtMs must be greater than timestampMs");
        }
    }
}
