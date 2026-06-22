package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflinePayReconcileCommand;
import io.korion.offlinepay.domain.status.OfflinePayReconcileCommandStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

public interface OfflinePayReconcileCommandRepository {

    OfflinePayReconcileCommand create(
            long userId,
            String assetCode,
            String reasonCode,
            String projectionVersion,
            String nonce,
            OffsetDateTime expiresAt,
            Map<String, Object> metadata
    );

    Optional<OfflinePayReconcileCommand> findRunnableByUserIdAndAssetCode(long userId, String assetCode, OffsetDateTime now);

    Optional<OfflinePayReconcileCommand> findByIdAndNonce(String id, String nonce);

    OfflinePayReconcileCommand markDelivered(String id, String deviceId);

    OfflinePayReconcileCommand markReported(
            String id,
            OfflinePayReconcileCommandStatus status,
            String deviceId,
            Map<String, Object> dryRunSummary,
            Map<String, Object> applySummary,
            Map<String, Object> localSummary,
            String errorMessage
    );
}
