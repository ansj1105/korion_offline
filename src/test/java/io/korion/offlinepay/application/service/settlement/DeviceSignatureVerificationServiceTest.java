package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DeviceSignatureVerificationServiceTest {

    private final DeviceSignatureVerificationService service = new DeviceSignatureVerificationService();

    @Test
    void verifiesEcDeviceSignatureWhenPublicKeyMatches() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        long timestamp = System.currentTimeMillis();
        String stateHash = "a".repeat(64);

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update((stateHash + "|" + timestamp + "|||").getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Device device = device(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        OfflinePaymentProof proof = proof(stateHash, timestamp, signature);

        DeviceSignatureVerificationService.VerificationResult result = service.verify(device, proof);

        assertTrue(result.verified());
        assertFalse(result.unsupported());
    }

    @Test
    void rejectsInvalidSignatureWhenPayloadDoesNotMatch() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        long timestamp = System.currentTimeMillis();
        String stateHash = "b".repeat(64);

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(("other-state" + "|" + timestamp + "|||").getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Device device = device(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        OfflinePaymentProof proof = proof(stateHash, timestamp, signature);

        DeviceSignatureVerificationService.VerificationResult result = service.verify(device, proof);

        assertFalse(result.verified());
        assertFalse(result.unsupported());
    }

    @Test
    void verifiesSignatureUsingBindingContextFromSpendingProofPayload() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        long timestamp = System.currentTimeMillis();
        String stateHash = "c".repeat(64);
        String rawPayload = """
                {
                  "spendingProof": {
                    "deviceRegistrationId": "row-1",
                    "signedUserId": "1",
                    "authMethod": "FINGERPRINT"
                  }
                }
                """;

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update((stateHash + "|" + timestamp + "|row-1|1|FINGERPRINT").getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Device device = device(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        OfflinePaymentProof proof = proof(stateHash, timestamp, signature, rawPayload, "{}");

        DeviceSignatureVerificationService.VerificationResult result = service.verify(device, proof);

        assertTrue(result.verified());
        assertFalse(result.unsupported());
    }

    @Test
    void verifiesSignatureUsingFrontendCanonicalPayloadBindingContext() throws Exception {
        verifyFrontendCanonicalFixtureForMode("BLE", "MANUAL_SELECTION", "MANUAL_PAYMENT");
        verifyFrontendCanonicalFixtureForMode("NFC", "FAST_CONTACT", "FAST_PAYMENT");
        verifyFrontendCanonicalFixtureForMode("QR", "QR_SCAN", "MANUAL_PAYMENT");
    }

    @Test
    void keepsFrontendSpendingProofSigningPayloadContractStable() {
        assertEquals("hash-new|1781181671841|registration-cached|39|FACE_ID",
                DeviceSignatureVerificationService.buildSigningPayloadForVerification(
                        "hash-new",
                        1781181671841L,
                        "registration-cached",
                        "39",
                        "FACE_ID"
                ));
        assertEquals("hash-new|1781181671841|||",
                DeviceSignatureVerificationService.buildSigningPayloadForVerification(
                        "hash-new",
                        1781181671841L,
                        null,
                        null,
                        null
                ));
        assertEquals("hash-new|1781181671841|registration-cached|39|FACE_ID",
                DeviceSignatureVerificationService.buildSigningPayloadForVerification(
                        "hash-new",
                        1781181671841L,
                        " registration-cached ",
                        " 39 ",
                        " FACE_ID "
                ));
        assertEquals(
                "b465f4818f228ce8abe2ae7fa5bce234ee96876d60d19b1be2d7ec451c72abe1",
                DeviceSignatureVerificationService.signingPayloadHashForVerification(
                        "hash-new|1781181671841|registration-cached|39|FACE_ID"
                )
        );
    }

    @Test
    void rejectsInconsistentBindingContextSourcesBeforeSignatureVerification() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        long timestamp = System.currentTimeMillis();
        String stateHash = "d".repeat(64);
        String rawPayload = """
                {
                  "deviceRegistrationId": "row-1",
                  "signedUserId": "1",
                  "authMethod": "FINGERPRINT",
                  "spendingProof": {
                    "deviceRegistrationId": "row-1",
                    "signedUserId": "2",
                    "authMethod": "FINGERPRINT"
                  }
                }
                """;

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update((stateHash + "|" + timestamp + "|row-1|1|FINGERPRINT").getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Device device = device(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        OfflinePaymentProof proof = proof(stateHash, timestamp, signature, rawPayload, "{}");

        DeviceSignatureVerificationService.VerificationResult result = service.verify(device, proof);

        assertFalse(result.verified());
        assertFalse(result.unsupported());
    }

    private void verifyFrontendCanonicalFixtureForMode(
            String mode,
            String connectionType,
            String paymentFlow
    ) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        long timestamp = 1781181671841L;
        String stateHash = "e".repeat(64);
        String canonicalPayload = """
                {
                  "voucherId": "voucher_1781181671841",
                  "deviceId": "device-1",
                  "counterpartyDeviceId": "device-2",
                  "amount": "10.000000",
                  "mode": "%s",
                  "connectionType": "%s",
                  "paymentFlow": "%s",
                  "direction": "SEND",
                  "deviceRegistrationId": "registration-cached",
                  "signedUserId": 39,
                  "authMethod": "FACE_ID",
                  "spendingProof": {
                    "deviceId": "device-1",
                    "amount": "10.000000",
                    "monotonicCounter": 1,
                    "nonce": "nonce-frontend",
                    "newStateHash": "%s",
                    "prevStateHash": "GENESIS",
                    "signature": "placeholder",
                    "timestamp": %d,
                    "deviceRegistrationId": "registration-cached",
                    "signedUserId": "39",
                    "authMethod": "FACE_ID"
                  }
                }
                """.formatted(mode, connectionType, paymentFlow, stateHash, timestamp);

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(DeviceSignatureVerificationService.buildSigningPayloadForVerification(
                stateHash,
                timestamp,
                "registration-cached",
                "39",
                "FACE_ID"
        ).getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Device device = device(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        OfflinePaymentProof proof = proof(stateHash, timestamp, signature, "{}", canonicalPayload);

        DeviceSignatureVerificationService.VerificationResult result = service.verify(device, proof);

        assertTrue(result.verified());
        assertFalse(result.unsupported());
    }

    @Test
    void includesComparableSigningPayloadHashWhenSignatureMismatches() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        long timestamp = 1781197644602L;
        String stateHash = "2e2c0ffabd51e9b199243c949fc9f62bcfdda5c2f062976401054ecf5d6a28e8";
        String rawPayload = """
                {
                  "deviceRegistrationId": "f27ba581-3df5-4b43-ae96-f17f676257f3",
                  "signedUserId": 39,
                  "authMethod": "FINGERPRINT"
                }
                """;

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update("different-payload".getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Device device = device(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        OfflinePaymentProof proof = proof(stateHash, timestamp, signature, rawPayload, "{}");

        DeviceSignatureVerificationService.VerificationResult result = service.verify(device, proof);

        assertFalse(result.verified());
        assertFalse(result.unsupported());
        assertTrue(result.detail().contains("signature mismatch"));
        assertTrue(result.detail().contains("signingPayloadHash="));
        assertTrue(result.detail().contains("deviceRegistrationId=f27ba581-3df5-4b43-ae96-f17f676257f3"));
        assertTrue(result.detail().contains("signedUserId=39"));
        assertTrue(result.detail().contains("authMethod=FINGERPRINT"));
    }

    private Device device(String publicKey) {
        return new Device(
                "row-1",
                "device-1",
                1L,
                publicKey,
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private OfflinePaymentProof proof(String newStateHash, long timestamp, String signature) {
        return proof(newStateHash, timestamp, signature, "{}", "{}");
    }

    private OfflinePaymentProof proof(
            String newStateHash,
            long timestamp,
            String signature,
            String rawPayload,
            String canonicalPayload
    ) {
        return new OfflinePaymentProof(
                "proof-1",
                "batch-1",
                "voucher-1",
                "collateral-1",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-1",
                newStateHash,
                "GENESIS",
                signature,
                new BigDecimal("10.00"),
                timestamp,
                timestamp + 60_000,
                canonicalPayload,
                "SENDER",
                rawPayload,
                OffsetDateTime.now()
        );
    }
}
