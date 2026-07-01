package io.korion.offlinepay.application.service.settlement;

import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
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
        if (incomingProof.policyVersion() != collateral.policyVersion()) {
            return new ChainValidationResult(false, OfflinePayReasonCode.POLICY_VERSION_MISMATCH, "{}");
        }
        if (incomingProof.amount().compareTo(collateral.remainingAmount()) > 0) {
            return new ChainValidationResult(false, OfflinePayReasonCode.INSUFFICIENT_REMAINING_AMOUNT, "{}");
        }
        List<String> acceptedNewStateHashes = spendingProofHashService.computeAcceptedNewStateHashes(
                incomingProof.previousHash(),
                incomingProof.amount(),
                incomingProof.monotonicCounter(),
                incomingProof.senderDeviceId(),
                incomingProof.nonce()
        );
        if (!acceptedNewStateHashes.contains(incomingProof.newStateHash())) {
            return new ChainValidationResult(
                    false,
                    OfflinePayReasonCode.INVALID_STATE_HASH,
                    jsonService.write(Map.of(
                            "expectedNewStateHash", acceptedNewStateHashes.get(0),
                            "legacyAcceptedNewStateHash", acceptedNewStateHashes.size() > 1 ? acceptedNewStateHashes.get(1) : acceptedNewStateHashes.get(0),
                            "actualNewStateHash", incomingProof.newStateHash()
                    ))
            );
        }
        if (existingProofs.isEmpty()) {
            if (!matchesInitialStateRoot(collateral, incomingProof)) {
                return new ChainValidationResult(false, OfflinePayReasonCode.INVALID_GENESIS_LINK, "{}");
            }
            return ChainValidationResult.success();
        }

        OfflinePaymentProof predecessor = existingProofs.stream()
                .filter(existing -> existing.monotonicCounter() == incomingProof.monotonicCounter() - 1)
                .max(Comparator.comparingLong(OfflinePaymentProof::monotonicCounter))
                .orElse(null);
        if (predecessor == null) {
            OfflinePaymentProof lastLowerProof = existingProofs.stream()
                    .filter(existing -> existing.monotonicCounter() < incomingProof.monotonicCounter())
                    .max(Comparator.comparingLong(OfflinePaymentProof::monotonicCounter))
                    .orElse(null);
            if (lastLowerProof == null) {
                if (!matchesInitialStateRoot(collateral, incomingProof)) {
                    return new ChainValidationResult(false, OfflinePayReasonCode.INVALID_GENESIS_LINK, "{}");
                }
            } else {
                return counterGap(lastLowerProof.monotonicCounter() + 1, incomingProof.monotonicCounter());
            }
        } else if (!predecessor.newStateHash().equals(incomingProof.prevStateHash())) {
            return new ChainValidationResult(
                    false,
                    OfflinePayReasonCode.INVALID_PREVIOUS_HASH,
                    jsonService.write(Map.of(
                            "expectedPreviousHash", predecessor.newStateHash(),
                            "actualPreviousHash", incomingProof.prevStateHash()
                    ))
            );
        }

        OfflinePaymentProof successor = existingProofs.stream()
                .filter(existing -> existing.monotonicCounter() == incomingProof.monotonicCounter() + 1)
                .min(Comparator.comparingLong(OfflinePaymentProof::monotonicCounter))
                .orElse(null);
        if (successor != null && !incomingProof.newStateHash().equals(successor.prevStateHash())) {
            return new ChainValidationResult(
                    false,
                    OfflinePayReasonCode.INVALID_PREVIOUS_HASH,
                    jsonService.write(Map.of(
                            "expectedSuccessorPreviousHash", incomingProof.newStateHash(),
                            "actualSuccessorPreviousHash", successor.prevStateHash(),
                            "successorCounter", successor.monotonicCounter()
                    ))
            );
        }
        return ChainValidationResult.success();
    }

    private ChainValidationResult counterGap(long expectedCounter, long actualCounter) {
        return new ChainValidationResult(
                false,
                OfflinePayReasonCode.COUNTER_GAP,
                jsonService.write(Map.of(
                        "expectedCounter", expectedCounter,
                        "actualCounter", actualCounter
                ))
        );
    }

    private boolean matchesInitialStateRoot(CollateralLock collateral, OfflinePaymentProof incomingProof) {
        if (collateral.initialStateRoot().equals(incomingProof.prevStateHash())) {
            return true;
        }
        if ("GENESIS".equals(collateral.initialStateRoot())
                && ("GENESIS:device:" + incomingProof.senderDeviceId()).equals(incomingProof.prevStateHash())) {
            return true;
        }
        return "AGGREGATED".equals(collateral.initialStateRoot())
                && "GENESIS".equals(incomingProof.prevStateHash());
    }
}
