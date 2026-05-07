package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ProofPayloadConsistencyValidatorTest {

    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final ProofPayloadConsistencyValidator validator = new ProofPayloadConsistencyValidator(jsonService);

    @Test
    void acceptsLegacySecondEpochExpiryAndMillisecondSpendingTimestamp() {
        long timestampMs = 1_778_164_128_344L;
        long expiresAtSeconds = 1_778_164_307L;
        long expiresAtMs = expiresAtSeconds * 1000L;
        String rawPayload = """
                {
                  "voucherId": "voucher-epoch",
                  "deviceId": "device-1",
                  "counterpartyDeviceId": "device-2",
                  "amount": "1.00",
                  "expiresAt": %d,
                  "spendingProof": {
                    "deviceId": "device-1",
                    "amount": "1.00",
                    "monotonicCounter": 18,
                    "nonce": "nonce-epoch",
                    "newStateHash": "hash-new",
                    "prevStateHash": "hash-prev",
                    "signature": "signature",
                    "timestamp": %d
                  }
                }
                """.formatted(expiresAtSeconds, timestampMs);
        String canonicalPayload = """
                {
                  "voucherId": "voucher-epoch",
                  "deviceId": "device-1",
                  "counterpartyDeviceId": "device-2"
                }
                """;

        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-epoch",
                "batch-epoch",
                "voucher-epoch",
                "collateral-epoch",
                "device-1",
                "device-2",
                1,
                1,
                18,
                "nonce-epoch",
                "hash-new",
                "hash-prev",
                "signature",
                new BigDecimal("1.00"),
                timestampMs,
                expiresAtMs,
                canonicalPayload,
                "SENDER",
                rawPayload,
                OffsetDateTime.now()
        );

        assertTrue(validator.validate(proof).passed());
    }
}
