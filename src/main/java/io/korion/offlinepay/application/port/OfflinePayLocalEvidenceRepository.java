package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflinePayLocalEvidence;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.time.OffsetDateTime;
import java.util.List;

public interface OfflinePayLocalEvidenceRepository {

    void save(OfflinePayLocalEvidence evidence);

    boolean existsMatchingReceiverEvidence(OfflinePaymentProof proof);

    void markMatchingReceiverEvidence(OfflinePaymentProof proof);

    void markMatchingEvidence(OfflinePaymentProof proof);

    List<OfflinePayLocalEvidence> findVerifiedSenderEvidenceAwaitingCarrier(int limit);

    LocalEvidenceStatus summarizeStatus(String voucherId, String sessionId, OffsetDateTime staleCutoff);

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
            int staleAwaitingCarrier,
            String oldestAwaitingCarrierAt,
            String latestUpdatedAt
    ) {}
}
