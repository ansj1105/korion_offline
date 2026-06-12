package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflinePayLocalEvidence;

public interface OfflinePayLocalEvidenceRepository {

    void save(OfflinePayLocalEvidence evidence);
}
