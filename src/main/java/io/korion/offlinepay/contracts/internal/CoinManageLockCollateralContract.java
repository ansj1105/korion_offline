package io.korion.offlinepay.contracts.internal;

public record CoinManageLockCollateralContract(
        String userId,
        String deviceId,
        String assetCode,
        String amount,
        String referenceId,
        int policyVersion
) {}
