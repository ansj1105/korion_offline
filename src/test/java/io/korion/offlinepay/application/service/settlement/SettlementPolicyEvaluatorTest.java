package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SettlementPolicyEvaluatorTest {

    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final SettlementPolicyEvaluator evaluator = new SettlementPolicyEvaluator(
            jsonService,
            new OfflinePaySettlementFeeCalculator()
    );

    @Test
    void usesCanonicalPolicyFieldsWhenRawPayloadOmitsClientPolicyFields() {
        String rawPayload = """
                {
                  "network": "TRC-20",
                  "token": "KORI",
                  "amount": "1.000000",
                  "paymentMethod": "QR",
                  "connectionType": "QR_SCAN"
                }
                """;
        String canonicalPayload = """
                {
                  "network": "TRC-20",
                  "token": "KORI",
                  "uiMode": "SEND",
                  "paymentFlow": "MANUAL_PAYMENT",
                  "connectionType": "QR_SCAN",
                  "availableAmount": "94.000000",
                  "ledgerExecutionMode": "INTERNAL_LEDGER_ONLY",
                  "senderAuthRequired": true,
                  "dualAmountEntered": false
                }
                """;

        SettlementEvaluation result = evaluator.evaluate(
                proof(rawPayload, canonicalPayload),
                collateral(),
                device()
        );

        assertEquals(SettlementStatus.SETTLED, result.status());
        assertTrue(result.resultJson().contains("\"uiMode\":\"SEND\""));
        assertTrue(result.resultJson().contains("\"paymentFlow\":\"MANUAL_PAYMENT\""));
        assertTrue(result.resultJson().contains("\"feeAmount\":0.004000"));
        assertTrue(result.resultJson().contains("\"settlementTotal\":1.004000"));
    }

    @Test
    void rejectsWhenPolicyFieldsAreMissingFromRawAndCanonicalPayloads() {
        SettlementEvaluation result = evaluator.evaluate(
                proof("{\"network\":\"TRC-20\",\"token\":\"KORI\"}", "{}"),
                collateral(),
                device()
        );

        assertEquals(SettlementStatus.REJECTED, result.status());
        assertEquals(OfflinePayReasonCode.PAYMENT_MODE_REQUIRED, result.reasonCode());
    }

    private OfflinePaymentProof proof(String rawPayload, String canonicalPayload) {
        long timestamp = System.currentTimeMillis();
        return new OfflinePaymentProof(
                "proof-policy",
                "batch-policy",
                "voucher-policy",
                "collateral-policy",
                "device-sender",
                "device-receiver",
                1,
                1,
                22L,
                "nonce-policy",
                "hash-policy",
                "GENESIS",
                "signature-policy",
                new BigDecimal("1.000000"),
                timestamp,
                timestamp + 60_000,
                canonicalPayload,
                "SENDER",
                "QR_SCAN",
                io.korion.offlinepay.domain.status.OfflineProofStatus.ISSUED,
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

    private CollateralLock collateral() {
        OffsetDateTime now = OffsetDateTime.now();
        return new CollateralLock(
                "collateral-policy",
                1L,
                "device-sender",
                "KORI",
                new BigDecimal("100.000000"),
                new BigDecimal("94.000000"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "external-lock",
                now.plusDays(1),
                "{}",
                now,
                now
        );
    }

    private Device device() {
        OffsetDateTime now = OffsetDateTime.now();
        return new Device(
                "registration-policy",
                "device-sender",
                1L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                now,
                now
        );
    }
}
