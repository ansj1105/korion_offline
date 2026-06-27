package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import java.math.BigDecimal;

final class CollateralLedgerAmountResolver {

    private CollateralLedgerAmountResolver() {
    }

    static BigDecimal resolveAdditionalCollateralAvailableAmount(
            CoinManageCollateralPort.BalanceSnapshot balanceSnapshot,
            FoxCoinWalletSnapshotPort.WalletSnapshot walletSnapshot,
            BigDecimal currentCollateralAmount
    ) {
        if (balanceSnapshot != null && balanceSnapshot.hasLedgerFootprint()) {
            return parseNonNegativeAmount(balanceSnapshot.availableBalance());
        }
        return CollateralAvailabilityCalculator.resolveAdditionalCollateralAvailableAmount(
                walletSnapshot,
                currentCollateralAmount
        );
    }

    static BigDecimal resolveSpendableCollateralAmount(
            CoinManageCollateralPort.BalanceSnapshot balanceSnapshot,
            BigDecimal offlineRemainingAmount
    ) {
        BigDecimal offlineRemaining = parseNonNegativeAmount(offlineRemainingAmount);
        if (balanceSnapshot != null && balanceSnapshot.hasLedgerFootprint()) {
            return offlineRemaining.min(parseNonNegativeAmount(balanceSnapshot.offlinePayPendingBalance()));
        }
        return offlineRemaining;
    }

    static BigDecimal parseNonNegativeAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value).max(BigDecimal.ZERO);
    }

    static BigDecimal parseNonNegativeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
