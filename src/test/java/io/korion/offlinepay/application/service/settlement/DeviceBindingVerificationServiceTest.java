package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class DeviceBindingVerificationServiceTest {

    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final DeviceBindingVerificationService service = new DeviceBindingVerificationService(jsonService);

    @Test
    void verifiesBindingContextFromSpendingProofPayload() {
        Device device = device();
        String rawPayload = """
                {
                  "deviceBindingKey": "%s",
                  "spendingProof": {
                    "deviceRegistrationId": "row-1",
                    "signedUserId": "1",
                    "authMethod": "FINGERPRINT"
                  }
                }
                """.formatted(DeviceBindingVerificationService.buildDeviceBindingKey(device));

        DeviceBindingVerificationService.VerificationResult result = service.verify(device, proof(rawPayload, "{}"));

        assertTrue(result.valid());
    }

    @Test
    void rejectsMismatchedBindingContextSources() {
        String rawPayload = """
                {
                  "deviceRegistrationId": "row-1",
                  "signedUserId": "1",
                  "authMethod": "FINGERPRINT",
                  "spendingProof": {
                    "deviceRegistrationId": "row-2",
                    "signedUserId": "1",
                    "authMethod": "FINGERPRINT"
                  }
                }
                """;

        DeviceBindingVerificationService.VerificationResult result = service.verify(device(), proof(rawPayload, "{}"));

        assertFalse(result.valid());
    }

    private Device device() {
        return new Device(
                "row-1",
                "device-1",
                1L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private OfflinePaymentProof proof(String rawPayload, String canonicalPayload) {
        long timestamp = System.currentTimeMillis();
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
                "a".repeat(64),
                "GENESIS",
                "signature",
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
