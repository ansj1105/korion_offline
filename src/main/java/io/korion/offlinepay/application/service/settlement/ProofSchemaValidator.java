package io.korion.offlinepay.application.service.settlement;

import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ProofSchemaValidator {

    public void validate(OfflinePaymentProof proof) {
        if (proof.voucherId() == null || proof.voucherId().isBlank()) {
            throw new IllegalArgumentException("voucherId is required");
        }
        if (proof.counter() < 1) {
            throw new IllegalArgumentException("counter must be >= 1");
        }
        if (proof.amount() == null || proof.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (proof.nonce() == null || proof.nonce().isBlank()) {
            throw new IllegalArgumentException("nonce is required");
        }
        if (proof.signature() == null || proof.signature().isBlank()) {
            throw new IllegalArgumentException("signature is required");
        }
        if (proof.hashChainHead() == null || proof.hashChainHead().isBlank()) {
            throw new IllegalArgumentException("hashChainHead is required");
        }
        if (!StringUtils.hasText(proof.rawPayloadJson())) {
            throw new IllegalArgumentException("payload is required");
        }
    }
}
