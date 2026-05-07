package io.korion.offlinepay.interfaces.http.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.korion.offlinepay.application.service.SettlementApplicationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SubmitSettlementBatchRequestTest {

    @Test
    void normalizesLegacySecondBasedEpochsBeforeApplicationServiceBoundary() {
        SubmitSettlementBatchRequest request = new SubmitSettlementBatchRequest(
                "SENDER",
                "sender-device",
                List.of(new SubmitSettlementBatchRequest.ProofRequest(
                        "voucher-epoch",
                        "collateral-epoch",
                        "sender-device",
                        "receiver-device",
                        "hash-new",
                        "hash-prev",
                        "signature",
                        new BigDecimal("1.00"),
                        1L,
                        1L,
                        18L,
                        "nonce-epoch",
                        1_778_164_128L,
                        1_778_164_307L,
                        "{}",
                        Map.of("paymentMethod", "QR")
                )),
                null
        );

        SettlementApplicationService.SubmitSettlementBatchCommand command = request.toCommand("idempotency-key");
        SettlementApplicationService.ProofSubmission proof = command.proofs().get(0);

        assertThat(proof.timestampMs()).isEqualTo(1_778_164_128_000L);
        assertThat(proof.expiresAtMs()).isEqualTo(1_778_164_307_000L);
    }

    @Test
    void keepsMillisecondEpochsUnchangedBeforeApplicationServiceBoundary() {
        SubmitSettlementBatchRequest request = new SubmitSettlementBatchRequest(
                "SENDER",
                "sender-device",
                List.of(new SubmitSettlementBatchRequest.ProofRequest(
                        "voucher-epoch",
                        "collateral-epoch",
                        "sender-device",
                        "receiver-device",
                        "hash-new",
                        "hash-prev",
                        "signature",
                        new BigDecimal("1.00"),
                        1L,
                        1L,
                        18L,
                        "nonce-epoch",
                        1_778_164_128_344L,
                        1_778_164_307_000L,
                        "{}",
                        Map.of("paymentMethod", "QR")
                )),
                null
        );

        SettlementApplicationService.SubmitSettlementBatchCommand command = request.toCommand("idempotency-key");
        SettlementApplicationService.ProofSubmission proof = command.proofs().get(0);

        assertThat(proof.timestampMs()).isEqualTo(1_778_164_128_344L);
        assertThat(proof.expiresAtMs()).isEqualTo(1_778_164_307_000L);
    }
}
