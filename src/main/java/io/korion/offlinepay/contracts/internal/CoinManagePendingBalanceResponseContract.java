package io.korion.offlinepay.contracts.internal;

public record CoinManagePendingBalanceResponseContract(
        String status,
        String userId,
        String assetCode,
        String availableBalance,
        String lockedBalance,
        String offlinePayPendingBalance
) {}
