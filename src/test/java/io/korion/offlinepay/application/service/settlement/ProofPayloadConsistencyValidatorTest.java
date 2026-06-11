package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void validatesHybridOfflineTimeEnvelope() {
        OfflinePaymentProof proof = proofWithHybridTime("2026-06-11T03:02:03Z");

        assertTrue(validator.validate(proof).passed());
    }

    @Test
    void rejectsHybridOfflineTimeWhenEstimatedTimeDoesNotMatchElapsedTime() {
        OfflinePaymentProof proof = proofWithHybridTime("2026-06-11T04:02:03Z");

        assertFalse(validator.validate(proof).passed());
    }

    @Test
    void rejectsPartialHybridOfflineTimeEnvelope() {
        String rawPayload = """
                {
                  "requestId": "req-hybrid-1",
                  "txId": "req-hybrid-1",
                  "offlineTxSequence": 12,
                  "voucherId": "voucher-hybrid",
                  "deviceId": "device-1",
                  "counterpartyDeviceId": "device-2",
                  "amount": "1.00",
                  "expiresAt": 1781184180000
                }
                """;
        String canonicalPayload = """
                {
                  "requestId": "req-hybrid-1",
                  "txId": "req-hybrid-1",
                  "voucherId": "voucher-hybrid",
                  "deviceId": "device-1",
                  "counterpartyDeviceId": "device-2"
                }
                """;

        assertFalse(validator.validate(proofWithPayloads(rawPayload, canonicalPayload)).passed());
    }

    @Test
    void rejectsHybridOfflineTimeWhenRawAndCanonicalSequenceDiffer() {
        OfflinePaymentProof proof = proofWithHybridTime(
                "2026-06-11T03:02:03Z",
                "req-hybrid-1",
                "req-hybrid-1",
                "req-hybrid-1",
                12,
                13
        );

        assertFalse(validator.validate(proof).passed());
    }

    @Test
    void rejectsHybridOfflineTimeWhenTxIdDoesNotMatchRequestId() {
        OfflinePaymentProof proof = proofWithHybridTime(
                "2026-06-11T03:02:03Z",
                "req-hybrid-1",
                "tx-hybrid-1",
                "tx-hybrid-1",
                12,
                12
        );

        assertFalse(validator.validate(proof).passed());
    }

    private OfflinePaymentProof proofWithHybridTime(String estimatedServerTime) {
        return proofWithHybridTime(
                estimatedServerTime,
                "req-hybrid-1",
                "req-hybrid-1",
                "req-hybrid-1",
                12,
                12
        );
    }

    private OfflinePaymentProof proofWithHybridTime(
            String estimatedServerTime,
            String requestId,
            String rawTxId,
            String canonicalTxId,
            long rawSequence,
            long canonicalSequence
    ) {
        long timestampMs = 1_781_184_120_000L;
        long expiresAtMs = timestampMs + 60_000L;
        String rawPayload = """
                {
                  "requestId": "%s",
                  "txId": "%s",
                  "offlineTxSequence": %d,
                  "deviceTime": "2026-06-11T12:02:03Z",
                  "lastServerSyncTime": "2026-06-11T03:00:00Z",
                  "estimatedServerTime": "%s",
                  "elapsedTimeMs": 123000,
                  "voucherId": "voucher-hybrid",
                  "deviceId": "device-1",
                  "counterpartyDeviceId": "device-2",
                  "amount": "1.00",
                  "expiresAt": %d,
                  "spendingProof": {
                    "deviceId": "device-1",
                    "amount": "1.00",
                    "monotonicCounter": 18,
                    "nonce": "nonce-hybrid",
                    "newStateHash": "hash-new",
                    "prevStateHash": "hash-prev",
                    "signature": "signature",
                    "timestamp": %d
                  }
                }
                """.formatted(requestId, rawTxId, rawSequence, estimatedServerTime, expiresAtMs, timestampMs);
        String canonicalPayload = """
                {
                  "requestId": "%s",
                  "txId": "%s",
                  "offlineTxSequence": %d,
                  "deviceTime": "2026-06-11T12:02:03Z",
                  "lastServerSyncTime": "2026-06-11T03:00:00Z",
                  "estimatedServerTime": "%s",
                  "elapsedTimeMs": 123000,
                  "voucherId": "voucher-hybrid",
                  "deviceId": "device-1",
                  "counterpartyDeviceId": "device-2"
                }
                """.formatted(requestId, canonicalTxId, canonicalSequence, estimatedServerTime);

        return proofWithPayloads(rawPayload, canonicalPayload);
    }

    private OfflinePaymentProof proofWithPayloads(String rawPayload, String canonicalPayload) {
        long timestampMs = 1_781_184_120_000L;
        long expiresAtMs = timestampMs + 60_000L;
        return new OfflinePaymentProof(
                "proof-hybrid",
                "batch-hybrid",
                "voucher-hybrid",
                "collateral-hybrid",
                "device-1",
                "device-2",
                1,
                1,
                18,
                "nonce-hybrid",
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
    }
}
