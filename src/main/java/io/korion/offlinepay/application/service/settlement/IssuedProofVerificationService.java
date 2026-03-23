package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class IssuedProofVerificationService {

    private final JsonService jsonService;
    private final IssuedOfflineProofRepository issuedOfflineProofRepository;

    public IssuedProofVerificationService(
            JsonService jsonService,
            IssuedOfflineProofRepository issuedOfflineProofRepository
    ) {
        this.jsonService = jsonService;
        this.issuedOfflineProofRepository = issuedOfflineProofRepository;
    }

    public VerificationResult verify(OfflinePaymentProof proof) {
        JsonNode rawPayload = jsonService.readTree(proof.rawPayloadJson());
        JsonNode canonicalPayload = jsonService.readTree(proof.canonicalPayload());
        JsonNode issuedNode = canonicalPayload.path("issuedProof");
        if (issuedNode.isMissingNode() || issuedNode.isNull()) {
            issuedNode = rawPayload.path("issuedProof");
        }
        if (issuedNode.isMissingNode() || issuedNode.isNull()) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_REQUIRED, "issuedProof missing");
        }

        String issuedProofId = text(issuedNode, "proofId");
        if (issuedProofId == null || issuedProofId.isBlank()) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_REQUIRED, "issuedProof.proofId missing");
        }
        if (isBlank(text(issuedNode, "issuerKeyId"))
                || isBlank(text(issuedNode, "issuerPublicKey"))
                || isBlank(text(issuedNode, "issuerSignature"))
                || isBlank(text(issuedNode, "issuedPayload"))
                || isBlank(text(issuedNode, "assetCode"))
                || decimal(issuedNode, "usableAmount") == null
                || isBlank(text(issuedNode, "collateralId"))
                || isBlank(text(issuedNode, "nonce"))) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_PAYLOAD_MISMATCH, "issued proof required fields missing");
        }

        Optional<IssuedOfflineProof> issuedProofOptional = issuedOfflineProofRepository.findById(issuedProofId);
        if (issuedProofOptional.isEmpty()) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_NOT_FOUND, "issued proof row missing");
        }

        IssuedOfflineProof issuedProof = issuedProofOptional.get();
        if (issuedProof.status() != IssuedProofStatus.ACTIVE) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_STATUS_INVALID, "issued proof is not active");
        }
        if (issuedProof.expiresAt() != null && issuedProof.expiresAt().isBefore(OffsetDateTime.now())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_EXPIRED, "issued proof expired");
        }
        if (!proof.senderDeviceId().equals(issuedProof.deviceId())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_DEVICE_MISMATCH, "issued proof device mismatch");
        }
        if (issuedProof.usableAmount().compareTo(proof.amount()) < 0) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_AMOUNT_EXCEEDED, "issued proof usable amount exceeded");
        }
        if (mismatch(text(issuedNode, "nonce"), issuedProof.proofNonce())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_NONCE_MISMATCH, "issued proof nonce mismatch");
        }
        if (mismatch(text(issuedNode, "issuerKeyId"), issuedProof.issuerKeyId())
                || mismatch(text(issuedNode, "issuerPublicKey"), issuedProof.issuerPublicKey())
                || mismatch(text(issuedNode, "issuerSignature"), issuedProof.issuerSignature())
                || mismatch(text(issuedNode, "issuedPayload"), issuedProof.issuedPayloadJson())
                || mismatch(text(issuedNode, "assetCode"), issuedProof.assetCode())
                || mismatch(decimal(issuedNode, "usableAmount"), issuedProof.usableAmount())
                || mismatch(text(issuedNode, "collateralId"), issuedProof.collateralId())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_PAYLOAD_MISMATCH, "issued proof payload mismatch");
        }

        JsonNode issuedPayloadNode = jsonService.readTree(issuedProof.issuedPayloadJson());
        if (mismatch(text(issuedPayloadNode, "proofId"), issuedProof.id())
                || mismatch(text(issuedPayloadNode, "deviceId"), issuedProof.deviceId())
                || mismatch(text(issuedPayloadNode, "collateralLockId"), issuedProof.collateralId())
                || mismatch(text(issuedPayloadNode, "assetCode"), issuedProof.assetCode())
                || mismatch(decimal(issuedPayloadNode, "usableAmount"), issuedProof.usableAmount())
                || mismatch(text(issuedPayloadNode, "nonce"), issuedProof.proofNonce())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_PAYLOAD_MISMATCH, "issued proof persisted payload mismatch");
        }

        return VerificationResult.valid(issuedProof);
    }

    public void markConsumed(OfflinePaymentProof proof) {
        VerificationResult verificationResult = verify(proof);
        if (!verificationResult.valid() || verificationResult.issuedProof() == null) {
            return;
        }
        issuedOfflineProofRepository.updateStatus(
                verificationResult.issuedProof().id(),
                IssuedProofStatus.CONSUMED,
                proof.id()
        );
    }

    public record VerificationResult(
            boolean valid,
            String reasonCode,
            String detail,
            IssuedOfflineProof issuedProof
    ) {
        public static VerificationResult valid(IssuedOfflineProof issuedProof) {
            return new VerificationResult(true, null, null, issuedProof);
        }

        public static VerificationResult invalid(String reasonCode, String detail) {
            return new VerificationResult(false, reasonCode, detail, null);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        return new BigDecimal(text);
    }

    private boolean mismatch(String left, String right) {
        return left != null && right != null && !left.equals(right);
    }

    private boolean mismatch(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) != 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
