package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.util.List;
import java.util.Optional;

public interface ReconciliationCaseRepository {

    ReconciliationCase save(
            String settlementId,
            String batchId,
            String proofId,
            String voucherId,
            String caseType,
            ReconciliationCaseStatus status,
            String reasonCode,
            String detailJson
    );

    List<ReconciliationCase> findRecent(int size, ReconciliationCaseStatus status, String caseType, String reasonCode);

    Optional<ReconciliationCase> findOpenBySettlementIdAndCaseType(String settlementId, String caseType);

    void resolve(String caseId, String detailJson);
}
