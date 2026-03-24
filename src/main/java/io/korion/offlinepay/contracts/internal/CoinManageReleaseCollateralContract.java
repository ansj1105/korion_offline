package io.korion.offlinepay.contracts.internal;

public record CoinManageReleaseCollateralContract(
        String userId,
        String deviceId,
        String collateralId,
        String assetCode,
        String amount,
        String referenceId
) {}
