package io.korion.offlinepay.application.service.settlement;

import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProofConflictDetector {

    private final JsonService jsonService;

    public ProofConflictDetector(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public ConflictDetectionResult detect(List<OfflinePaymentProof> existingProofs, OfflinePaymentProof incomingProof) {
        for (OfflinePaymentProof existingProof : existingProofs) {
            if (existingProof.counter() == incomingProof.counter()
                    && !existingProof.hashChainHead().equals(incomingProof.hashChainHead())) {
                return new ConflictDetectionResult(
                        true,
                        OfflinePayReasonCode.DUPLICATE_COUNTER,
                        jsonService.write(Map.of(
                                "existingProofId", existingProof.id(),
                                "incomingProofId", incomingProof.id() == null ? "" : incomingProof.id(),
                                "counter", incomingProof.counter()
                        ))
                );
            }
            if (existingProof.nonce().equals(incomingProof.nonce())
                    && !existingProof.voucherId().equals(incomingProof.voucherId())) {
                return new ConflictDetectionResult(
                        true,
                        OfflinePayReasonCode.DUPLICATE_NONCE,
                        jsonService.write(Map.of(
                                "existingVoucherId", existingProof.voucherId(),
                                "incomingVoucherId", incomingProof.voucherId(),
                                "nonce", incomingProof.nonce()
                        ))
                );
            }
        }
        return ConflictDetectionResult.clear();
    }
}
