package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.CollateralStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CollateralRepository {

    record CollateralBalanceSummary(
            long userId,
            String assetCode,
            BigDecimal lockedAmount,
            BigDecimal remainingAmount
    ) {}

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
            String metadataJson
    );

    Optional<CollateralLock> findById(String collateralId);

    Optional<CollateralLock> findAggregateByUserIdAndAssetCode(long userId, String assetCode);

    List<CollateralLock> findActiveByUserIdAndAssetCode(long userId, String assetCode);

    List<CollateralBalanceSummary> summarizeActiveBalances(String assetCode, int size);

    void deductLockedAndRemainingAmount(String collateralId, BigDecimal amount);

    void deductRemainingAmount(String collateralId, BigDecimal amount);

    void updateStatus(String collateralId, CollateralStatus status, String metadataJson);
}
