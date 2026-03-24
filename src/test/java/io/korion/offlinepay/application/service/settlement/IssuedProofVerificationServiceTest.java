package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.application.service.ProofIssuerSignatureService;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IssuedProofVerificationServiceTest {

    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final ProofIssuerSignatureService proofIssuerSignatureService = new ProofIssuerSignatureService(
            new AppProperties(
                    "USDT",
                    24,
                    100,
                    1000,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            )
    );
    private final IssuedOfflineProofRepository issuedOfflineProofRepository = Mockito.mock(IssuedOfflineProofRepository.class);
    private final IssuedProofVerificationService service = new IssuedProofVerificationService(
            jsonService,
            issuedOfflineProofRepository,
            proofIssuerSignatureService
    );

    @Test
    void verifyAcceptsIssuedProofWithValidIssuerSignature() {
        IssuedOfflineProof issuedProof = buildIssuedProof(false);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(issuedProof));

        IssuedProofVerificationService.VerificationResult result = service.verify(buildIncomingProof(issuedProof));

        assertTrue(result.valid());
        assertEquals("issued-proof-1", result.issuedProof().id());
    }

    @Test
    void verifyRejectsIssuedProofWithInvalidIssuerSignature() {
        IssuedOfflineProof issuedProof = buildIssuedProof(true);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(issuedProof));

        IssuedProofVerificationService.VerificationResult result = service.verify(buildIncomingProof(issuedProof));

        assertEquals(OfflinePayReasonCode.ISSUED_PROOF_SIGNATURE_INVALID, result.reasonCode());
    }

    private IssuedOfflineProof buildIssuedProof(boolean tamperSignature) {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
        Map<String, Object> issuedPayloadMap = new LinkedHashMap<>();
        issuedPayloadMap.put("proofId", "issued-proof-1");
        issuedPayloadMap.put("userId", 77L);
        issuedPayloadMap.put("deviceId", "device-1");
        issuedPayloadMap.put("collateralLockId", "collateral-1");
        issuedPayloadMap.put("assetCode", "USDT");
        issuedPayloadMap.put("usableAmount", "500.000000");
        issuedPayloadMap.put("issuedAt", OffsetDateTime.now().toString());
        issuedPayloadMap.put("expiresAt", expiresAt.toString());
        issuedPayloadMap.put("nonce", "proof-nonce-1");
        issuedPayloadMap.put("devicePublicKey", "sender-device-public-key");
        issuedPayloadMap.put("issuerKeyId", proofIssuerSignatureService.keyId());
        String issuedPayload = jsonService.write(issuedPayloadMap);
        String signature = proofIssuerSignatureService.sign(issuedPayload);
        if (tamperSignature) {
            signature = signature.substring(0, signature.length() - 2) + "ab";
        }
        return new IssuedOfflineProof(
                "issued-proof-1",
                77L,
                "device-1",
                "collateral-1",
                "USDT",
                new BigDecimal("500.000000"),
                "proof-nonce-1",
                proofIssuerSignatureService.keyId(),
                proofIssuerSignatureService.publicKey(),
                signature,
                issuedPayload,
                IssuedProofStatus.ACTIVE,
                null,
                expiresAt,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private OfflinePaymentProof buildIncomingProof(IssuedOfflineProof issuedProof) {
        String canonicalPayload = jsonService.write(Map.of(
                "issuedProof",
                Map.of(
                        "proofId", issuedProof.id(),
                        "issuerKeyId", issuedProof.issuerKeyId(),
                        "issuerPublicKey", issuedProof.issuerPublicKey(),
                        "issuerSignature", issuedProof.issuerSignature(),
                        "issuedPayload", issuedProof.issuedPayloadJson(),
                        "assetCode", issuedProof.assetCode(),
                        "usableAmount", issuedProof.usableAmount().toPlainString(),
                        "collateralId", issuedProof.collateralId(),
                        "nonce", issuedProof.proofNonce()
                )
        ));
        String rawPayload = jsonService.write(Map.of(
                "senderDevice", Map.of("publicKey", "sender-device-public-key"),
                "issuedProof",
                Map.of(
                        "proofId", issuedProof.id(),
                        "issuerKeyId", issuedProof.issuerKeyId(),
                        "issuerPublicKey", issuedProof.issuerPublicKey(),
                        "issuerSignature", issuedProof.issuerSignature(),
                        "issuedPayload", issuedProof.issuedPayloadJson(),
                        "assetCode", issuedProof.assetCode(),
                        "usableAmount", issuedProof.usableAmount().toPlainString(),
                        "collateralId", issuedProof.collateralId(),
                        "nonce", issuedProof.proofNonce()
                )
        ));
        return new OfflinePaymentProof(
                "proof-row-1",
                "batch-1",
                "voucher-1",
                "collateral-1",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "payment-nonce-1",
                "hash-1",
                "prev-hash-1",
                "sender-signature-1",
                new BigDecimal("100.000000"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                canonicalPayload,
                "SENDER",
                "BLE",
                OfflineProofStatus.ISSUED,
                null,
                rawPayload,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
