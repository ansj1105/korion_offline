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

    java.util.Optional<ReconciliationCase> findById(String id);

    List<ReconciliationCase> findRecent(int size, ReconciliationCaseStatus status, String caseType, String reasonCode);

    List<ReconciliationCase> findOpenRetryable(int size);

    Optional<ReconciliationCase> findOpenBySettlementIdAndCaseType(String settlementId, String caseType);

    Optional<ReconciliationCase> findOpenByBatchIdAndCaseType(String batchId, String caseType);

    Optional<ReconciliationCase> findOpenByVoucherIdAndCaseType(String voucherId, String caseType);

    void resolve(String caseId, String detailJson);

    void updateDetail(String caseId, String detailJson);
}
