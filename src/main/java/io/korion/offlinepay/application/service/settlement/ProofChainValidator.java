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
    private final SpendingProofHashService spendingProofHashService;

    public ProofChainValidator(JsonService jsonService, SpendingProofHashService spendingProofHashService) {
        this.jsonService = jsonService;
        this.spendingProofHashService = spendingProofHashService;
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
        String expectedNewStateHash = spendingProofHashService.computeNewStateHash(
                incomingProof.previousHash(),
                incomingProof.amount(),
                incomingProof.monotonicCounter(),
                incomingProof.senderDeviceId(),
                incomingProof.nonce()
        );
        if (!expectedNewStateHash.equals(incomingProof.newStateHash())) {
            return new ChainValidationResult(
                    false,
                    "INVALID_STATE_HASH",
                    jsonService.write(Map.of(
                            "expectedNewStateHash", expectedNewStateHash,
                            "actualNewStateHash", incomingProof.newStateHash()
                    ))
            );
        }
        if (existingProofs.isEmpty()) {
            if (incomingProof.monotonicCounter() != 1) {
                return new ChainValidationResult(false, "INVALID_GENESIS_COUNTER", "{}");
            }
            if (!collateral.initialStateRoot().equals(incomingProof.prevStateHash())) {
                return new ChainValidationResult(false, "INVALID_GENESIS_LINK", "{}");
            }
            return ChainValidationResult.success();
        }

        OfflinePaymentProof lastProof = existingProofs.stream()
                .max(Comparator.comparingLong(OfflinePaymentProof::monotonicCounter))
                .orElseThrow();
        if (incomingProof.monotonicCounter() != lastProof.monotonicCounter() + 1) {
            return new ChainValidationResult(
                    false,
                    "COUNTER_GAP",
                    jsonService.write(Map.of(
                            "expectedCounter", lastProof.monotonicCounter() + 1,
                            "actualCounter", incomingProof.monotonicCounter()
                    ))
            );
        }
        if (!lastProof.newStateHash().equals(incomingProof.prevStateHash())) {
            return new ChainValidationResult(
                    false,
                    "INVALID_PREVIOUS_HASH",
                    jsonService.write(Map.of(
                            "expectedPreviousHash", lastProof.newStateHash(),
                            "actualPreviousHash", incomingProof.prevStateHash()
                    ))
            );
        }
        return ChainValidationResult.success();
    }
}
