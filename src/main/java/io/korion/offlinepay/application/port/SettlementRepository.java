package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository {

    SettlementRequest save(String batchId, String collateralId, String proofId, SettlementStatus status, String reasonCode, boolean conflictDetected, String settlementResultJson);

    List<SettlementRequest> findByBatchId(String batchId);

    Optional<SettlementRequest> findById(String settlementId);

    boolean existsOpenByCollateralId(String collateralId);

    void update(String settlementId, SettlementStatus status, String reasonCode, boolean conflictDetected, String settlementResultJson);
}
