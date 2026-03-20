package io.korion.offlinepay.contracts.internal;

import java.math.BigDecimal;

public record CoinManageReleaseCollateralContract(
        long userId,
        String deviceId,
        String collateralId,
        String assetCode,
        BigDecimal amount,
        String referenceId
) {}
