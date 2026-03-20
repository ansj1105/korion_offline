package io.korion.offlinepay.application.port;

import java.math.BigDecimal;

public interface CoinManageCollateralPort {

    LockCollateralResult lockCollateral(long userId, String deviceId, String assetCode, BigDecimal amount, String referenceId, int policyVersion);

    record LockCollateralResult(String lockId, String status) {}
}
