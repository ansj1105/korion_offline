package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.OfflinePayLocalEvidenceRepository;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.OfflinePayLocalEvidence;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOfflinePayLocalEvidenceRepository implements OfflinePayLocalEvidenceRepository {

    private final JdbcClient jdbcClient;
    private final JsonService jsonService;

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
                    CASE WHEN :matchedProofId IS NULL THEN NULL ELSE NOW() END
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
                WHERE direction = 'RECEIVE'
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
        Integer count = jdbcClient.sql(sql)
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
                .param("counterText", String.valueOf(proof.counter()))
                .query(Integer.class)
                .single();
        return count != null && count > 0;
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
}
