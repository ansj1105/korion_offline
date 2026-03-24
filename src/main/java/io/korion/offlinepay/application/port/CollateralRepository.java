package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.CollateralStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface CollateralRepository {

    CollateralLock save(
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal lockedAmount,
            BigDecimal remainingAmount,
            String initialStateRoot,
            int policyVersion,
            CollateralStatus status,
            String externalLockId,
            OffsetDateTime expiresAt,
            String metadataJson
    );

    Optional<CollateralLock> findById(String collateralId);

    Optional<CollateralLock> findLatestByUserIdAndDeviceIdAndAssetCode(long userId, String deviceId, String assetCode);

    Optional<CollateralLock> findAggregateByUserIdAndDeviceIdAndAssetCode(long userId, String deviceId, String assetCode);

    void deductRemainingAmount(String collateralId, BigDecimal amount);

    void updateStatus(String collateralId, CollateralStatus status, String metadataJson);
}
