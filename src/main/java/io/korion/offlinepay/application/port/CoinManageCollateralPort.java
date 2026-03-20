package io.korion.offlinepay.application.port;

import java.math.BigDecimal;

public interface CoinManageCollateralPort {

    LockCollateralResult lockCollateral(long userId, String deviceId, String assetCode, BigDecimal amount, String referenceId, int policyVersion);

    ReleaseCollateralResult releaseCollateral(long userId, String deviceId, String collateralId, String assetCode, BigDecimal amount, String referenceId);

    record LockCollateralResult(String lockId, String status) {}

    record ReleaseCollateralResult(String releaseId, String status) {}
}
