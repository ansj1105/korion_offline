package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflinePayLocalEvidence;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;

public interface OfflinePayLocalEvidenceRepository {

    void save(OfflinePayLocalEvidence evidence);

    boolean existsMatchingReceiverEvidence(OfflinePaymentProof proof);
}
