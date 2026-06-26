package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflinePayLocalEvidenceRepository;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.OfflinePayLocalEvidence;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOfflinePayLocalEvidenceRepository implements OfflinePayLocalEvidenceRepository {

    private final JdbcClient jdbcClient;
    private final JsonService jsonService;
    private final RowMapper<OfflinePayLocalEvidence> rowMapper = (rs, rowNum) -> new OfflinePayLocalEvidence(
            rs.getString("proof_id"),
            rs.getString("voucher_id"),
            rs.getString("session_id"),
            rs.getString("direction"),
            rs.getString("uploader_type"),
            rs.getString("uploader_device_id"),
            rs.getString("sender_device_id"),
            rs.getString("receiver_device_id"),
            rs.getBigDecimal("amount"),
            rs.getObject("counter") == null ? null : rs.getLong("counter"),
            rs.getString("previous_hash"),
            rs.getString("hash_chain_head"),
            rs.getString("nonce"),
            rs.getString("signature"),
            rs.getString("canonical_payload"),
            readPayload(rs.getString("raw_payload")),
            rs.getString("verification_status"),
            rs.getString("verification_detail"),
            rs.getString("matched_proof_id")
    );

    public JdbcOfflinePayLocalEvidenceRepository(JdbcClient jdbcClient, JsonService jsonService) {
        this.jdbcClient = jdbcClient;
        this.jsonService = jsonService;
    }

    @Override
    public void save(OfflinePayLocalEvidence evidence) {
        String sql = """
                INSERT INTO offline_pay_local_evidence (
                    proof_id,
                    voucher_id,
                    session_id,
                    direction,
                    uploader_type,
                    uploader_device_id,
                    sender_device_id,
                    receiver_device_id,
                    amount,
                    counter,
                    previous_hash,
                    hash_chain_head,
                    nonce,
                    signature,
                    canonical_payload,
                    raw_payload,
                    verification_status,
                    verification_detail,
                    matched_proof_id,
                    matched_at
                ) VALUES (
                    :proofId,
                    :voucherId,
                    :sessionId,
                    :direction,
                    :uploaderType,
                    :uploaderDeviceId,
                    :senderDeviceId,
                    :receiverDeviceId,
                    :amount,
                    :counter,
                    :previousHash,
                    :hashChainHead,
                    :nonce,
                    :signature,
                    :canonicalPayload,
                    CAST(:rawPayload AS jsonb),
                    :verificationStatus,
                    :verificationDetail,
                    :matchedProofId,
                    CASE WHEN CAST(:matchedProofId AS uuid) IS NULL THEN NULL ELSE NOW() END
                )
                ON CONFLICT (voucher_id, direction, uploader_device_id, nonce)
                DO UPDATE SET
                    proof_id = EXCLUDED.proof_id,
                    session_id = EXCLUDED.session_id,
                    sender_device_id = EXCLUDED.sender_device_id,
                    receiver_device_id = EXCLUDED.receiver_device_id,
                    amount = EXCLUDED.amount,
                    counter = EXCLUDED.counter,
                    previous_hash = EXCLUDED.previous_hash,
                    hash_chain_head = EXCLUDED.hash_chain_head,
                    signature = EXCLUDED.signature,
                    canonical_payload = EXCLUDED.canonical_payload,
                    raw_payload = EXCLUDED.raw_payload,
                    verification_status = EXCLUDED.verification_status,
                    verification_detail = EXCLUDED.verification_detail,
                    matched_proof_id = EXCLUDED.matched_proof_id,
                    matched_at = CASE
                        WHEN EXCLUDED.matched_proof_id IS NULL THEN offline_pay_local_evidence.matched_at
                        ELSE COALESCE(offline_pay_local_evidence.matched_at, NOW())
                    END,
                    updated_at = NOW()
                """;

        jdbcClient.sql(sql)
                .param("proofId", parseUuid(evidence.proofId()))
                .param("voucherId", evidence.voucherId())
                .param("sessionId", blankToNull(evidence.sessionId()))
                .param("direction", evidence.direction())
                .param("uploaderType", evidence.uploaderType())
                .param("uploaderDeviceId", evidence.uploaderDeviceId())
                .param("senderDeviceId", evidence.senderDeviceId())
                .param("receiverDeviceId", evidence.receiverDeviceId())
                .param("amount", evidence.amount())
                .param("counter", evidence.counter())
                .param("previousHash", blankToNull(evidence.previousHash()))
                .param("hashChainHead", blankToNull(evidence.hashChainHead()))
                .param("nonce", evidence.nonce())
                .param("signature", blankToNull(evidence.signature()))
                .param("canonicalPayload", blankToNull(evidence.canonicalPayload()))
                .param("rawPayload", jsonService.write(evidence.rawPayload()))
                .param("verificationStatus", evidence.verificationStatus())
                .param("verificationDetail", blankToNull(evidence.verificationDetail()))
                .param("matchedProofId", parseUuid(evidence.matchedProofId()))
                .update();
    }

    @Override
    public boolean existsMatchingReceiverEvidence(OfflinePaymentProof proof) {
        String sql = """
                SELECT COUNT(*)
                FROM offline_pay_local_evidence
                WHERE %s
                """.formatted(matchingReceiverEvidencePredicate());
        Integer count = bindMatchingReceiverEvidenceParams(jdbcClient.sql(sql), proof)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void markMatchingReceiverEvidence(OfflinePaymentProof proof) {
        String sql = """
                UPDATE offline_pay_local_evidence
                SET proof_id = COALESCE(proof_id, :proofId),
                    matched_proof_id = :proofId,
                    matched_at = COALESCE(matched_at, NOW()),
                    updated_at = NOW()
                WHERE %s
                """;
        bindMatchingReceiverEvidenceParams(jdbcClient.sql(sql.formatted(matchingReceiverEvidencePredicate())), proof)
                .update();
    }

    @Override
    public void markMatchingEvidence(OfflinePaymentProof proof) {
        String sql = """
                UPDATE offline_pay_local_evidence
                SET proof_id = COALESCE(proof_id, :proofId),
                    matched_proof_id = :proofId,
                    matched_at = COALESCE(matched_at, NOW()),
                    updated_at = NOW()
                WHERE verification_status = 'VERIFIED'
                  AND voucher_id = :voucherId
                  AND sender_device_id = :senderDeviceId
                  AND receiver_device_id = :receiverDeviceId
                  AND amount = :amount
                  AND (
                      (
                          direction = 'SEND'
                          AND hash_chain_head = :hashChainHead
                          AND COALESCE(previous_hash, '') = COALESCE(:previousHash, '')
                          AND nonce = :nonce
                          AND signature = :signature
                          AND counter::text = :counterText
                      )
                      OR (
                          direction = 'RECEIVE'
                          AND (
                              matched_proof_id = :proofId
                              OR proof_id = :proofId
                              OR raw_payload ->> 'proofId' = :proofIdText
                              OR (
                                  raw_payload ->> 'receiverEvidenceBlockSenderProofNewHash' = :hashChainHead
                                  AND raw_payload ->> 'receiverEvidenceBlockSenderProofPrevHash' = :previousHash
                                  AND raw_payload ->> 'receiverEvidenceBlockSenderProofNonce' = :nonce
                                  AND raw_payload ->> 'receiverEvidenceBlockSenderProofSignature' = :signature
                                  AND raw_payload ->> 'receiverEvidenceBlockSenderProofCounter' = :counterText
                              )
                              OR (
                                  raw_payload ->> 'receiverLocalBlockNewHash' = :hashChainHead
                                  AND raw_payload ->> 'receiverLocalBlockPrevHash' = :previousHash
                                  AND raw_payload ->> 'receiverLocalBlockNonce' = :nonce
                                  AND raw_payload ->> 'receiverLocalBlockSignature' = :signature
                                  AND raw_payload ->> 'receiverLocalBlockCounter' = :counterText
                              )
                          )
                      )
                  )
                """;
        bindMatchingReceiverEvidenceParams(jdbcClient.sql(sql), proof).update();
    }

    @Override
    public List<OfflinePayLocalEvidence> findVerifiedSenderEvidenceAwaitingCarrier(int limit) {
        String sql = """
                SELECT s.*
                FROM offline_pay_local_evidence s
                WHERE s.verification_status = 'VERIFIED'
                  AND (
                      (
                          s.matched_proof_id IS NULL
                          AND NOT EXISTS (
                              SELECT 1
                              FROM offline_payment_proofs p
                              WHERE p.voucher_id = s.voucher_id
                          )
                      )
                      OR (
                          s.direction = 'RECEIVE'
                          AND EXISTS (
                              SELECT 1
                              FROM offline_payment_proofs p
                              JOIN settlement_requests sr ON sr.proof_id = p.id
                              WHERE p.voucher_id = s.voucher_id
                                AND p.sender_device_id = s.sender_device_id
                                AND p.receiver_device_id = s.receiver_device_id
                                AND p.amount = s.amount
                                AND p.status = 'REJECTED'
                                AND sr.status = 'REJECTED'
                                AND COALESCE(sr.conflict_detected, FALSE) = FALSE
                                AND COALESCE(sr.settlement_result ->> 'financiallyHonored', 'false') <> 'true'
                          )
                      )
                  )
                  AND (
                      s.direction = 'SEND'
                      OR (
                          s.direction = 'RECEIVE'
                          AND COALESCE(s.raw_payload ->> 'senderProofCanonicalPayload', s.raw_payload ->> 'receiverEvidenceBlockSenderProofCanonicalPayload', '') <> ''
                          AND COALESCE(s.raw_payload ->> 'senderProofSignature', s.raw_payload ->> 'receiverEvidenceBlockSenderProofSignature', '') <> ''
                          AND COALESCE(s.raw_payload ->> 'senderProofNewHash', s.raw_payload ->> 'receiverEvidenceBlockSenderProofNewHash', '') <> ''
                          AND COALESCE(s.raw_payload ->> 'senderProofNonce', s.raw_payload ->> 'receiverEvidenceBlockSenderProofNonce', '') <> ''
                      )
                  )
                ORDER BY s.created_at ASC
                LIMIT :limit
                """;
        return jdbcClient.sql(sql)
                .param("limit", Math.max(1, limit))
                .query(rowMapper)
                .list();
    }

    @Override
    public LocalEvidenceStatus summarizeStatus(String voucherId, String sessionId, OffsetDateTime staleCutoff) {
        String normalizedVoucherId = blankToNull(voucherId);
        String normalizedSessionId = blankToNull(sessionId);
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(MAX(voucher_id), :voucherId) AS voucher_id,
                    COALESCE(MAX(session_id), :sessionId) AS session_id,
                    COUNT(*)::int AS total,
                    COALESCE(SUM(CASE WHEN verification_status = 'VERIFIED' THEN 1 ELSE 0 END), 0)::int AS stored,
                    COALESCE(SUM(CASE WHEN verification_status = 'VERIFIED' AND matched_proof_id IS NOT NULL THEN 1 ELSE 0 END), 0)::int AS matched,
                    COALESCE(SUM(CASE WHEN verification_status = 'VERIFIED' AND matched_proof_id IS NULL THEN 1 ELSE 0 END), 0)::int AS awaiting_carrier,
                    COALESCE(SUM(CASE WHEN verification_status <> 'VERIFIED' THEN 1 ELSE 0 END), 0)::int AS failed,
                    COALESCE(SUM(CASE WHEN direction = 'SEND' AND verification_status = 'VERIFIED' THEN 1 ELSE 0 END), 0)::int AS sender_stored,
                    COALESCE(SUM(CASE WHEN direction = 'RECEIVE' AND verification_status = 'VERIFIED' THEN 1 ELSE 0 END), 0)::int AS receiver_stored,
                    COALESCE(SUM(CASE WHEN direction = 'SEND' AND verification_status = 'VERIFIED' AND matched_proof_id IS NOT NULL THEN 1 ELSE 0 END), 0)::int AS sender_matched,
                    COALESCE(SUM(CASE WHEN direction = 'RECEIVE' AND verification_status = 'VERIFIED' AND matched_proof_id IS NOT NULL THEN 1 ELSE 0 END), 0)::int AS receiver_matched,
                    COALESCE(SUM(CASE WHEN direction = 'SEND' AND verification_status <> 'VERIFIED' THEN 1 ELSE 0 END), 0)::int AS sender_failed,
                    COALESCE(SUM(CASE WHEN direction = 'RECEIVE' AND verification_status <> 'VERIFIED' THEN 1 ELSE 0 END), 0)::int AS receiver_failed,
                    COALESCE(SUM(CASE
                        WHEN verification_status = 'VERIFIED'
                         AND matched_proof_id IS NULL
                         AND updated_at < :staleCutoff
                        THEN 1 ELSE 0 END), 0)::int AS stale_awaiting_carrier,
                    MIN(CASE
                        WHEN verification_status = 'VERIFIED'
                         AND matched_proof_id IS NULL
                        THEN updated_at ELSE NULL END)::text AS oldest_awaiting_carrier_at,
                    MAX(updated_at)::text AS latest_updated_at
                FROM offline_pay_local_evidence
                WHERE 1 = 1
                """);
        if (normalizedVoucherId != null) {
            sql.append(" AND voucher_id = :voucherId\n");
        }
        if (normalizedSessionId != null) {
            sql.append(" AND session_id = :sessionId\n");
        }
        return jdbcClient.sql(sql.toString())
                .param("voucherId", normalizedVoucherId)
                .param("sessionId", normalizedSessionId)
                .param("staleCutoff", staleCutoff == null ? OffsetDateTime.now().minusHours(24) : staleCutoff)
                .query((rs, rowNum) -> new LocalEvidenceStatus(
                        rs.getString("voucher_id"),
                        rs.getString("session_id"),
                        rs.getInt("total"),
                        rs.getInt("stored"),
                        rs.getInt("matched"),
                        rs.getInt("awaiting_carrier"),
                        rs.getInt("failed"),
                        rs.getInt("sender_stored"),
                        rs.getInt("receiver_stored"),
                        rs.getInt("sender_matched"),
                        rs.getInt("receiver_matched"),
                        rs.getInt("sender_failed"),
                        rs.getInt("receiver_failed"),
                        rs.getInt("stale_awaiting_carrier"),
                        rs.getString("oldest_awaiting_carrier_at"),
                        rs.getString("latest_updated_at")
                ))
                .single();
    }

    private String matchingReceiverEvidencePredicate() {
        return """
                direction = 'RECEIVE'
                  AND verification_status = 'VERIFIED'
                  AND voucher_id = :voucherId
                  AND sender_device_id = :senderDeviceId
                  AND receiver_device_id = :receiverDeviceId
                  AND amount = :amount
                  AND (
                      matched_proof_id = :proofId
                      OR proof_id = :proofId
                      OR raw_payload ->> 'proofId' = :proofIdText
                      OR (
                          raw_payload ->> 'receiverEvidenceBlockSenderProofNewHash' = :hashChainHead
                          AND raw_payload ->> 'receiverEvidenceBlockSenderProofPrevHash' = :previousHash
                          AND raw_payload ->> 'receiverEvidenceBlockSenderProofNonce' = :nonce
                          AND raw_payload ->> 'receiverEvidenceBlockSenderProofSignature' = :signature
                          AND raw_payload ->> 'receiverEvidenceBlockSenderProofCounter' = :counterText
                      )
                      OR (
                          raw_payload ->> 'receiverLocalBlockNewHash' = :hashChainHead
                          AND raw_payload ->> 'receiverLocalBlockPrevHash' = :previousHash
                          AND raw_payload ->> 'receiverLocalBlockNonce' = :nonce
                          AND raw_payload ->> 'receiverLocalBlockSignature' = :signature
                          AND raw_payload ->> 'receiverLocalBlockCounter' = :counterText
                      )
                  )
                """;
    }

    private JdbcClient.StatementSpec bindMatchingReceiverEvidenceParams(JdbcClient.StatementSpec spec, OfflinePaymentProof proof) {
        return spec
                .param("voucherId", proof.voucherId())
                .param("senderDeviceId", proof.senderDeviceId())
                .param("receiverDeviceId", proof.receiverDeviceId())
                .param("amount", proof.amount())
                .param("proofId", parseUuid(proof.id()))
                .param("proofIdText", proof.id())
                .param("hashChainHead", proof.hashChainHead())
                .param("previousHash", proof.previousHash())
                .param("nonce", proof.nonce())
                .param("signature", proof.signature())
                .param("counterText", String.valueOf(proof.counter()));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return jsonService.readMap(json);
    }
}
