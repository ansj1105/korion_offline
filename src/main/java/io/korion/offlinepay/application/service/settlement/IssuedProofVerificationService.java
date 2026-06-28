package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.application.service.IssuedProofApplicationService;
import io.korion.offlinepay.application.service.JsonPayloadCanonicalizationService;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.application.service.ProofIssuerSignatureService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class IssuedProofVerificationService {

    private final JsonService jsonService;
    private final JsonPayloadCanonicalizationService jsonPayloadCanonicalizationService;
    private final IssuedOfflineProofRepository issuedOfflineProofRepository;
    private final CollateralRepository collateralRepository;
    private final ProofIssuerSignatureService proofIssuerSignatureService;

    public IssuedProofVerificationService(
            JsonService jsonService,
            JsonPayloadCanonicalizationService jsonPayloadCanonicalizationService,
            IssuedOfflineProofRepository issuedOfflineProofRepository,
            CollateralRepository collateralRepository,
            ProofIssuerSignatureService proofIssuerSignatureService
    ) {
        this.jsonService = jsonService;
        this.jsonPayloadCanonicalizationService = jsonPayloadCanonicalizationService;
        this.issuedOfflineProofRepository = issuedOfflineProofRepository;
        this.collateralRepository = collateralRepository;
        this.proofIssuerSignatureService = proofIssuerSignatureService;
    }

    public VerificationResult verify(OfflinePaymentProof proof) {
        JsonNode rawPayload = jsonService.readTree(proof.rawPayloadJson());
        JsonNode canonicalPayload = jsonService.readTree(proof.canonicalPayload());
        JsonNode issuedNode = canonicalPayload.path("issuedProof");
        if (issuedNode.isMissingNode() || issuedNode.isNull()) {
            issuedNode = rawPayload.path("issuedProof");
        }
        JsonNode senderDeviceNode = rawPayload.path("senderDevice");
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
                || isBlank(text(issuedNode, "nonce"))
                || isBlank(text(senderDeviceNode, "publicKey"))) {
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
        if (!proof.senderDeviceId().equals(issuedProof.deviceId())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_DEVICE_MISMATCH, "issued proof device mismatch");
        }
        if (issuedProof.usableAmount().compareTo(proof.amount()) < 0) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_AMOUNT_EXCEEDED, "issued proof usable amount exceeded");
        }
        if (mismatch(text(issuedNode, "nonce"), issuedProof.proofNonce())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_NONCE_MISMATCH, "issued proof nonce mismatch");
        }
        String signedIssuedPayload = text(issuedNode, "issuedPayload");
        if (mismatch(text(issuedNode, "issuerKeyId"), issuedProof.issuerKeyId())
                || mismatch(text(issuedNode, "issuerPublicKey"), issuedProof.issuerPublicKey())
                || mismatch(text(issuedNode, "issuerSignature"), issuedProof.issuerSignature())
                || !jsonPayloadCanonicalizationService.sameJson(signedIssuedPayload, issuedProof.issuedPayloadJson())
                || mismatch(text(issuedNode, "assetCode"), issuedProof.assetCode())
                || mismatch(decimal(issuedNode, "usableAmount"), issuedProof.usableAmount())
                || mismatch(text(issuedNode, "collateralId"), issuedProof.collateralId())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_PAYLOAD_MISMATCH, "issued proof payload mismatch");
        }

        JsonNode issuedPayloadNode = jsonService.readTree(signedIssuedPayload);
        if (mismatch(text(issuedPayloadNode, "proofId"), issuedProof.id())
                || mismatch(number(issuedPayloadNode, "userId"), issuedProof.userId())
                || mismatch(text(issuedPayloadNode, "subjectBindingKey"), IssuedProofApplicationService.buildSubjectBindingKey(issuedProof.userId(), issuedProof.assetCode()))
                || mismatch(text(issuedPayloadNode, "deviceId"), issuedProof.deviceId())
                || mismatch(text(issuedPayloadNode, "collateralLockId"), issuedProof.collateralId())
                || mismatch(text(issuedPayloadNode, "assetCode"), issuedProof.assetCode())
                || mismatch(decimal(issuedPayloadNode, "usableAmount"), issuedProof.usableAmount())
                || mismatch(text(issuedPayloadNode, "nonce"), issuedProof.proofNonce())
                || mismatch(text(issuedPayloadNode, "issuerKeyId"), issuedProof.issuerKeyId())
                || mismatch(text(issuedPayloadNode, "devicePublicKey"), text(senderDeviceNode, "publicKey"))
                || isBlank(text(issuedPayloadNode, "issuedAt"))
                || mismatch(text(issuedPayloadNode, "expiresAt"), issuedProof.expiresAt() == null ? null : issuedProof.expiresAt().toString())) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_PAYLOAD_MISMATCH, "issued proof persisted payload mismatch");
        }
        if (!proofIssuerSignatureService.verify(
                signedIssuedPayload,
                issuedProof.issuerPublicKey(),
                issuedProof.issuerSignature()
        ) && !proofIssuerSignatureService.verify(
                jsonPayloadCanonicalizationService.canonicalize(signedIssuedPayload),
                issuedProof.issuerPublicKey(),
                issuedProof.issuerSignature()
        )) {
            return VerificationResult.invalid(OfflinePayReasonCode.ISSUED_PROOF_SIGNATURE_INVALID, "issued proof issuer signature invalid");
        }
        Optional<VerificationResult> collateralBackingFailure = verifyCurrentCollateralBacking(
                issuedProof,
                issuedPayloadNode,
                proof.amount()
        );
        if (collateralBackingFailure.isPresent()) {
            return collateralBackingFailure.get();
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

    private Long number(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        return Long.parseLong(text);
    }

    private Optional<VerificationResult> verifyCurrentCollateralBacking(
            IssuedOfflineProof issuedProof,
            JsonNode issuedPayloadNode,
            BigDecimal paymentAmount
    ) {
        Set<String> proofCollateralIds = readCollateralLockIds(issuedPayloadNode, issuedProof.collateralId());
        if (proofCollateralIds.isEmpty()) {
            return Optional.of(VerificationResult.invalid(
                    OfflinePayReasonCode.ISSUED_PROOF_PAYLOAD_MISMATCH,
                    "issued proof collateral ids missing"
            ));
        }
        List<CollateralLock> activeCollaterals = Optional.ofNullable(
                collateralRepository.findActiveByUserIdAndAssetCode(issuedProof.userId(), issuedProof.assetCode())
        ).orElse(List.of());
        BigDecimal activeBackingRemaining = BigDecimal.ZERO;
        for (String collateralId : proofCollateralIds) {
            Optional<CollateralLock> matched = activeCollaterals.stream()
                    .filter(collateral -> collateralId.equals(collateral.id()))
                    .findFirst();
            if (matched.isEmpty() && issuedProof.collateralId().equals(collateralId)) {
                return Optional.of(VerificationResult.invalid(
                        OfflinePayReasonCode.ISSUED_PROOF_STATUS_INVALID,
                        "issued proof collateral is not active"
                ));
            }
            if (matched.isPresent()) {
                activeBackingRemaining = activeBackingRemaining.add(matched.get().remainingAmount());
            }
        }
        if (activeBackingRemaining.compareTo(paymentAmount) < 0) {
            return Optional.of(VerificationResult.invalid(
                    OfflinePayReasonCode.ISSUED_PROOF_AMOUNT_EXCEEDED,
                    "payment amount exceeds active collateral"
            ));
        }
        return Optional.empty();
    }

    private Set<String> readCollateralLockIds(JsonNode issuedPayloadNode, String fallbackCollateralId) {
        Set<String> result = new LinkedHashSet<>();
        JsonNode collateralLockIds = issuedPayloadNode.path("collateralLockIds");
        if (collateralLockIds.isArray()) {
            collateralLockIds.forEach(node -> {
                String id = node.asText("");
                if (!id.isBlank()) {
                    result.add(id);
                }
            });
        }
        String legacyLockId = text(issuedPayloadNode, "collateralLockId");
        if (result.isEmpty() && legacyLockId != null) {
            result.add(legacyLockId);
        }
        if (result.isEmpty() && fallbackCollateralId != null && !fallbackCollateralId.isBlank()) {
            result.add(fallbackCollateralId);
        }
        return result;
    }

    private boolean mismatch(String left, String right) {
        return left != null && right != null && !left.equals(right);
    }

    private boolean mismatch(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) != 0;
    }

    private boolean mismatch(Long left, long right) {
        return left != null && left != right;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
