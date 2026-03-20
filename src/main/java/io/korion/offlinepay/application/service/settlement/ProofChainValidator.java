package io.korion.offlinepay.application.service.settlement;

import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProofChainValidator {

    private final JsonService jsonService;

    public ProofChainValidator(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public ChainValidationResult validate(CollateralLock collateral, List<OfflinePaymentProof> existingProofs, OfflinePaymentProof incomingProof) {
        if (collateral.expiresAt().isBefore(OffsetDateTime.now())) {
            return new ChainValidationResult(false, "COLLATERAL_EXPIRED", "{}");
        }
        if (incomingProof.policyVersion() != collateral.policyVersion()) {
            return new ChainValidationResult(false, "POLICY_VERSION_MISMATCH", "{}");
        }
        if (incomingProof.amount().compareTo(collateral.remainingAmount()) > 0) {
            return new ChainValidationResult(false, "INSUFFICIENT_REMAINING_AMOUNT", "{}");
        }
        if (existingProofs.isEmpty()) {
            if (incomingProof.counter() != 1) {
                return new ChainValidationResult(false, "INVALID_GENESIS_COUNTER", "{}");
            }
            if (!collateral.initialStateRoot().equals(incomingProof.previousHash())) {
                return new ChainValidationResult(false, "INVALID_GENESIS_LINK", "{}");
            }
            return ChainValidationResult.success();
        }

        OfflinePaymentProof lastProof = existingProofs.stream()
                .max(Comparator.comparingLong(OfflinePaymentProof::counter))
                .orElseThrow();
        if (incomingProof.counter() != lastProof.counter() + 1) {
            return new ChainValidationResult(
                    false,
                    "COUNTER_GAP",
                    jsonService.write(Map.of(
                            "expectedCounter", lastProof.counter() + 1,
                            "actualCounter", incomingProof.counter()
                    ))
            );
        }
        if (!lastProof.hashChainHead().equals(incomingProof.previousHash())) {
            return new ChainValidationResult(
                    false,
                    "INVALID_PREVIOUS_HASH",
                    jsonService.write(Map.of(
                            "expectedPreviousHash", lastProof.hashChainHead(),
                            "actualPreviousHash", incomingProof.previousHash()
                    ))
            );
        }
        return ChainValidationResult.success();
    }
}
