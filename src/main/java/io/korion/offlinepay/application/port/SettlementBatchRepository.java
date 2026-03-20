package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementStatusMetric;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import java.util.List;
import java.util.Optional;

public interface SettlementBatchRepository {

    SettlementBatch save(String sourceDeviceId, String idempotencyKey, SettlementBatchStatus status, int proofsCount, String summaryJson);

    Optional<SettlementBatch> findById(String batchId);

    Optional<SettlementBatch> findByIdempotencyKey(String idempotencyKey);

    void updateStatus(String batchId, SettlementBatchStatus status, String summaryJson);

    List<SettlementBatch> findPendingValidationBatches(int limit);

    List<SettlementBatch> findRecentConflictedBatches(int limit);

    List<SettlementBatch> findDeadLetterBatches(int limit, String networkScope);

    List<SettlementStatusMetric> summarizeStatusByHour(int hours, String networkScope);

    List<SettlementBatch> findRecentBatches(int limit, String networkScope);

    long countDeadLetterBatches(int hours, String networkScope);
}
