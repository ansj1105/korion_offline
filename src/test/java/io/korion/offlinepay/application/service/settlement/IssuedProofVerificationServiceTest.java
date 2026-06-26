package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.application.service.IssuedProofApplicationService;
import io.korion.offlinepay.application.service.JsonPayloadCanonicalizationService;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.application.service.ProofIssuerSignatureService;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IssuedProofVerificationServiceTest {

    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final JsonPayloadCanonicalizationService jsonPayloadCanonicalizationService =
            new JsonPayloadCanonicalizationService(jsonService);
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
    private final CollateralRepository collateralRepository = Mockito.mock(CollateralRepository.class);
    private final IssuedProofVerificationService service = new IssuedProofVerificationService(
            jsonService,
            jsonPayloadCanonicalizationService,
            issuedOfflineProofRepository,
            collateralRepository,
            proofIssuerSignatureService
    );

    @Test
    void verifyAcceptsIssuedProofWithValidIssuerSignature() {
        IssuedOfflineProof issuedProof = buildIssuedProof(false);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(issuedProof));
        givenActiveCollateralBacking("collateral-1", "500.000000");

        IssuedProofVerificationService.VerificationResult result = service.verify(buildIncomingProof(issuedProof));

        assertTrue(result.valid());
        assertEquals("issued-proof-1", result.issuedProof().id());
    }

    @Test
    void verifyAcceptsActiveIssuedProofAfterExpiresAt() {
        IssuedProofFixture fixture = buildIssuedProofFixture(false, false, OffsetDateTime.now().minusHours(1));
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(fixture.issuedProof()));
        givenActiveCollateralBacking("collateral-1", "500.000000");

        IssuedProofVerificationService.VerificationResult result = service.verify(
                buildIncomingProof(fixture.issuedProof(), fixture.signedPayload())
        );

        assertTrue(result.valid());
    }

    @Test
    void verifyAcceptsIssuedProofWithoutExpiresAt() {
        IssuedProofFixture fixture = buildIssuedProofFixture(false, false, null);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(fixture.issuedProof()));
        givenActiveCollateralBacking("collateral-1", "500.000000");

        IssuedProofVerificationService.VerificationResult result = service.verify(
                buildIncomingProof(fixture.issuedProof(), fixture.signedPayload())
        );

        assertTrue(result.valid());
    }

    @Test
    void verifyAcceptsSignedPayloadWhenDatabaseJsonbNormalizesStoredPayload() {
        IssuedProofFixture fixture = buildIssuedProofFixture(false);
        IssuedOfflineProof normalizedStoredProof = withPersistedPayload(
                fixture.issuedProof(),
                jsonService.readTree(fixture.signedPayload()).toPrettyString()
        );
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(normalizedStoredProof));
        givenActiveCollateralBacking("collateral-1", "500.000000");

        IssuedProofVerificationService.VerificationResult result = service.verify(
                buildIncomingProof(normalizedStoredProof, fixture.signedPayload())
        );

        assertTrue(result.valid());
        assertEquals("issued-proof-1", result.issuedProof().id());
    }

    @Test
    void verifyAcceptsCanonicalSignedPayloadAfterJsonbReordersFields() {
        IssuedProofFixture fixture = buildCanonicalIssuedProofFixture(false);
        String reorderedPayload = """
                {"nonce":"proof-nonce-1","userId":77,"proofId":"issued-proof-1","deviceId":"device-1","issuedAt":"%s","assetCode":"USDT","expiresAt":"%s","issuerKeyId":"%s","usableAmount":"500.000000","devicePublicKey":"sender-device-public-key","collateralLockId":"collateral-1","subjectBindingKey":"%s"}
                """.formatted(
                text(fixture.signedPayload(), "issuedAt"),
                text(fixture.signedPayload(), "expiresAt"),
                proofIssuerSignatureService.keyId(),
                IssuedProofApplicationService.buildSubjectBindingKey(77L, "USDT")
        ).trim();
        IssuedOfflineProof normalizedStoredProof = withPersistedPayload(fixture.issuedProof(), reorderedPayload);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(normalizedStoredProof));
        givenActiveCollateralBacking("collateral-1", "500.000000");

        IssuedProofVerificationService.VerificationResult result = service.verify(
                buildIncomingProof(normalizedStoredProof, reorderedPayload)
        );

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

    @Test
    void verifyRejectsIssuedProofWhenSenderPublicKeyMissing() {
        IssuedOfflineProof issuedProof = buildIssuedProof(false);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(issuedProof));

        OfflinePaymentProof incoming = buildIncomingProofWithoutSenderPublicKey(issuedProof);
        IssuedProofVerificationService.VerificationResult result = service.verify(incoming);

        assertEquals(OfflinePayReasonCode.ISSUED_PROOF_PAYLOAD_MISMATCH, result.reasonCode());
    }

    @Test
    void verifyRejectsIssuedProofWhenBackingCollateralIsNoLongerActive() {
        IssuedOfflineProof issuedProof = buildIssuedProof(false);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(issuedProof));
        when(collateralRepository.findActiveByUserIdAndAssetCode(77L, "USDT")).thenReturn(List.of());

        IssuedProofVerificationService.VerificationResult result = service.verify(buildIncomingProof(issuedProof));

        assertEquals(OfflinePayReasonCode.ISSUED_PROOF_STATUS_INVALID, result.reasonCode());
    }

    @Test
    void verifyAcceptsIssuedProofWhenBackingCollateralCoversPaymentAmount() {
        IssuedOfflineProof issuedProof = buildIssuedProof(false);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(issuedProof));
        givenActiveCollateralBacking("collateral-1", "499.999999");

        IssuedProofVerificationService.VerificationResult result = service.verify(buildIncomingProof(issuedProof));

        assertTrue(result.valid());
    }

    @Test
    void verifyRejectsIssuedProofWhenBackingCollateralIsBelowPaymentAmount() {
        IssuedOfflineProof issuedProof = buildIssuedProof(false);
        when(issuedOfflineProofRepository.findById("issued-proof-1")).thenReturn(Optional.of(issuedProof));
        givenActiveCollateralBacking("collateral-1", "99.999999");

        IssuedProofVerificationService.VerificationResult result = service.verify(buildIncomingProof(issuedProof));

        assertEquals(OfflinePayReasonCode.ISSUED_PROOF_AMOUNT_EXCEEDED, result.reasonCode());
    }

    private IssuedOfflineProof buildIssuedProof(boolean tamperSignature) {
        return buildIssuedProofFixture(tamperSignature).issuedProof();
    }

    private IssuedProofFixture buildIssuedProofFixture(boolean tamperSignature) {
        return buildIssuedProofFixture(tamperSignature, false);
    }

    private IssuedProofFixture buildCanonicalIssuedProofFixture(boolean tamperSignature) {
        return buildIssuedProofFixture(tamperSignature, true);
    }

    private IssuedProofFixture buildIssuedProofFixture(boolean tamperSignature, boolean canonicalSignature) {
        return buildIssuedProofFixture(tamperSignature, canonicalSignature, OffsetDateTime.now().plusHours(1));
    }

    private IssuedProofFixture buildIssuedProofFixture(boolean tamperSignature, boolean canonicalSignature, OffsetDateTime expiresAt) {
        Map<String, Object> issuedPayloadMap = new LinkedHashMap<>();
        issuedPayloadMap.put("proofId", "issued-proof-1");
        issuedPayloadMap.put("userId", 77L);
        issuedPayloadMap.put("subjectBindingKey", IssuedProofApplicationService.buildSubjectBindingKey(77L, "USDT"));
        issuedPayloadMap.put("deviceId", "device-1");
        issuedPayloadMap.put("collateralLockId", "collateral-1");
        issuedPayloadMap.put("assetCode", "USDT");
        issuedPayloadMap.put("usableAmount", "500.000000");
        issuedPayloadMap.put("issuedAt", OffsetDateTime.now().toString());
        issuedPayloadMap.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        issuedPayloadMap.put("nonce", "proof-nonce-1");
        issuedPayloadMap.put("devicePublicKey", "sender-device-public-key");
        issuedPayloadMap.put("issuerKeyId", proofIssuerSignatureService.keyId());
        String issuedPayload = jsonService.write(issuedPayloadMap);
        String signedPayload = canonicalSignature
                ? jsonPayloadCanonicalizationService.canonicalize(issuedPayload)
                : issuedPayload;
        String signature = proofIssuerSignatureService.sign(signedPayload);
        if (tamperSignature) {
            signature = signature.substring(0, signature.length() - 2) + "ab";
        }
        IssuedOfflineProof issuedProof = new IssuedOfflineProof(
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
        return new IssuedProofFixture(issuedProof, issuedPayload);
    }

    private OfflinePaymentProof buildIncomingProof(IssuedOfflineProof issuedProof) {
        return buildIncomingProof(issuedProof, issuedProof.issuedPayloadJson());
    }

    private OfflinePaymentProof buildIncomingProof(IssuedOfflineProof issuedProof, String signedPayload) {
        String canonicalPayload = jsonService.write(Map.of(
                "issuedProof",
                Map.of(
                        "proofId", issuedProof.id(),
                        "issuerKeyId", issuedProof.issuerKeyId(),
                        "issuerPublicKey", issuedProof.issuerPublicKey(),
                        "issuerSignature", issuedProof.issuerSignature(),
                        "issuedPayload", signedPayload,
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
                        "issuedPayload", signedPayload,
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

    private IssuedOfflineProof withPersistedPayload(IssuedOfflineProof issuedProof, String persistedPayload) {
        return new IssuedOfflineProof(
                issuedProof.id(),
                issuedProof.userId(),
                issuedProof.deviceId(),
                issuedProof.collateralId(),
                issuedProof.assetCode(),
                issuedProof.usableAmount(),
                issuedProof.proofNonce(),
                issuedProof.issuerKeyId(),
                issuedProof.issuerPublicKey(),
                issuedProof.issuerSignature(),
                persistedPayload,
                issuedProof.status(),
                issuedProof.consumedByProofId(),
                issuedProof.expiresAt(),
                issuedProof.createdAt(),
                issuedProof.updatedAt()
        );
    }

    private void givenActiveCollateralBacking(String collateralId, String remainingAmount) {
        when(collateralRepository.findActiveByUserIdAndAssetCode(77L, "USDT"))
                .thenReturn(List.of(activeCollateral(collateralId, remainingAmount)));
    }

    private CollateralLock activeCollateral(String collateralId, String remainingAmount) {
        OffsetDateTime now = OffsetDateTime.now();
        return new CollateralLock(
                collateralId,
                77L,
                "device-1",
                "USDT",
                new BigDecimal(remainingAmount),
                new BigDecimal(remainingAmount),
                "state-root-1",
                1,
                CollateralStatus.LOCKED,
                null,
                now.plusHours(1),
                "{}",
                now,
                now
        );
    }

    private String text(String json, String field) {
        return jsonService.readTree(json).path(field).asText();
    }

    private OfflinePaymentProof buildIncomingProofWithoutSenderPublicKey(IssuedOfflineProof issuedProof) {
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
                "proof-row-2",
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

    private record IssuedProofFixture(
            IssuedOfflineProof issuedProof,
            String signedPayload
    ) {}
}
