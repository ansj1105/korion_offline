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

    @Test
    void preservesZeroExpiryAsOfflineSettlementNoExpirySentinel() {
        SubmitSettlementBatchRequest request = new SubmitSettlementBatchRequest(
                "SENDER",
                "sender-device",
                List.of(new SubmitSettlementBatchRequest.ProofRequest(
                        "voucher-no-expiry",
                        "collateral-no-expiry",
                        "sender-device",
                        "receiver-device",
                        "hash-new",
                        "hash-prev",
                        "signature",
                        new BigDecimal("1.00"),
                        1L,
                        1L,
                        18L,
                        "nonce-no-expiry",
                        1_778_164_128_344L,
                        0L,
                        "{}",
                        Map.of(
                                "paymentMethod", "BLE",
                                "expiresAtPolicy", "NO_OFFLINE_PROOF_EXPIRY",
                                "offlineProofNoExpiry", true
                        )
                )),
                null
        );

        SettlementApplicationService.SubmitSettlementBatchCommand command = request.toCommand("idempotency-key");
        SettlementApplicationService.ProofSubmission proof = command.proofs().get(0);

        assertThat(proof.timestampMs()).isEqualTo(1_778_164_128_344L);
        assertThat(proof.expiresAtMs()).isZero();
    }

    @Test
    void usesSignedSpendingProofAsCanonicalProofFieldsWhenLocalBlockCountersDiffer() {
        SubmitSettlementBatchRequest request = new SubmitSettlementBatchRequest(
                "SENDER",
                "sender-device",
                List.of(new SubmitSettlementBatchRequest.ProofRequest(
                        "voucher-recovered",
                        "collateral-recovered",
                        "sender-device",
                        "receiver-device",
                        "local-block-new-hash",
                        "local-block-prev-hash",
                        "local-block-signature",
                        new BigDecimal("5.00"),
                        1L,
                        1L,
                        72L,
                        "local-block-nonce",
                        1_782_488_128_000L,
                        0L,
                        "{}",
                        Map.of(
                                "localBlockCounter", 72,
                                "localBlockNewHash", "local-block-new-hash",
                                "localBlockPrevHash", "local-block-prev-hash",
                                "localBlockNonce", "local-block-nonce",
                                "localBlockSignature", "local-block-signature",
                                "spendingProof", Map.of(
                                        "monotonicCounter", 67,
                                        "newStateHash", "spending-proof-new-hash",
                                        "prevStateHash", "spending-proof-prev-hash",
                                        "nonce", "spending-proof-nonce",
                                        "signature", "spending-proof-signature",
                                        "timestamp", 1_782_488_128_111L
                                )
                        )
                )),
                null
        );

        SettlementApplicationService.SubmitSettlementBatchCommand command = request.toCommand("idempotency-key");
        SettlementApplicationService.ProofSubmission proof = command.proofs().get(0);

        assertThat(proof.counter()).isEqualTo(67L);
        assertThat(proof.hashChainHead()).isEqualTo("spending-proof-new-hash");
        assertThat(proof.previousHash()).isEqualTo("spending-proof-prev-hash");
        assertThat(proof.nonce()).isEqualTo("spending-proof-nonce");
        assertThat(proof.signature()).isEqualTo("spending-proof-signature");
        assertThat(proof.timestampMs()).isEqualTo(1_782_488_128_111L);
    }
}
