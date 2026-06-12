package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflinePayLocalEvidence;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.util.List;

public interface OfflinePayLocalEvidenceRepository {

    void save(OfflinePayLocalEvidence evidence);

    boolean existsMatchingReceiverEvidence(OfflinePaymentProof proof);

    void markMatchingReceiverEvidence(OfflinePaymentProof proof);

    List<OfflinePayLocalEvidence> findVerifiedSenderEvidenceWithMatchingReceiverEvidence(int limit);

    LocalEvidenceStatus summarizeStatus(String voucherId, String sessionId);

    record LocalEvidenceStatus(
            String voucherId,
            String sessionId,
            int total,
            int stored,
            int matched,
            int awaitingCarrier,
            int failed,
            int senderStored,
            int receiverStored,
            int senderMatched,
            int receiverMatched,
            int senderFailed,
            int receiverFailed,
            String latestUpdatedAt
    ) {}
}
