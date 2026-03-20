package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CollateralOperationRepository {

    CollateralOperation saveRequested(
            String collateralId,
            long userId,
            String deviceId,
            String assetCode,
            CollateralOperationType operationType,
            BigDecimal amount,
            String referenceId,
            String metadataJson
    );

    void markCompleted(String referenceId, String collateralId, String metadataJson);

    void markFailed(String referenceId, String errorMessage, String metadataJson);

    Optional<CollateralOperation> findByReferenceId(String referenceId);

    List<CollateralOperation> findRecent(
            int size,
            CollateralOperationType operationType,
            CollateralOperationStatus status,
            String assetCode
    );
}
