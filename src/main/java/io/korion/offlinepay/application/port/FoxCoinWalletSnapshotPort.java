package io.korion.offlinepay.application.port;

import java.math.BigDecimal;

public interface FoxCoinWalletSnapshotPort {

    WalletSnapshot getCanonicalWalletSnapshot(long userId, String assetCode);

    record WalletSnapshot(
            long userId,
            String assetCode,
            BigDecimal totalBalance,
            BigDecimal lockedBalance,
            String canonicalBasis,
            String refreshedAt
    ) {}
}
