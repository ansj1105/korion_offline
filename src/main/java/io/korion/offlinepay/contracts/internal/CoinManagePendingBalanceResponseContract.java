package io.korion.offlinepay.contracts.internal;

public record CoinManagePendingBalanceResponseContract(
        String status,
        String userId,
        String assetCode,
        String offlinePayPendingBalance
) {}
