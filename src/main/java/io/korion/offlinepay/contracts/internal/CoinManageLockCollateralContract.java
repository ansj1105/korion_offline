package io.korion.offlinepay.contracts.internal;

import java.math.BigDecimal;

public record CoinManageLockCollateralContract(
        long userId,
        String deviceId,
        String assetCode,
        BigDecimal amount,
        String referenceId,
        int policyVersion
) {}
