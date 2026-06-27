package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import java.math.BigDecimal;

final class CollateralAvailabilityCalculator {

    private CollateralAvailabilityCalculator() {
    }

    static BigDecimal resolveAdditionalCollateralAvailableAmount(
            FoxCoinWalletSnapshotPort.WalletSnapshot walletSnapshot,
            BigDecimal currentCollateralAmount
    ) {
        if (walletSnapshot == null || walletSnapshot.totalBalance() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalBalance = walletSnapshot.totalBalance();
        BigDecimal collateralAmount = currentCollateralAmount == null
                ? BigDecimal.ZERO
                : currentCollateralAmount.max(BigDecimal.ZERO);
        return totalBalance.subtract(collateralAmount).max(BigDecimal.ZERO);
    }

}
