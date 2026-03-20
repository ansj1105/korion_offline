package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
                "{}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );
    }
}
