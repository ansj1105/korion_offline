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

    static BigDecimal capByLedgerAvailableAmount(
            BigDecimal additionalCollateralAvailableAmount,
            BigDecimal ledgerAvailableAmount,
            BigDecimal ledgerLockedAmount,
            BigDecimal ledgerOfflinePayPendingAmount
    ) {
        BigDecimal available = additionalCollateralAvailableAmount == null
                ? BigDecimal.ZERO
                : additionalCollateralAvailableAmount.max(BigDecimal.ZERO);
        BigDecimal ledgerAvailable = ledgerAvailableAmount == null ? BigDecimal.ZERO : ledgerAvailableAmount.max(BigDecimal.ZERO);
        BigDecimal ledgerLocked = ledgerLockedAmount == null ? BigDecimal.ZERO : ledgerLockedAmount.max(BigDecimal.ZERO);
        BigDecimal ledgerOfflinePayPending = ledgerOfflinePayPendingAmount == null
                ? BigDecimal.ZERO
                : ledgerOfflinePayPendingAmount.max(BigDecimal.ZERO);
        boolean ledgerHasOfflinePayFootprint = ledgerAvailable.signum() > 0
                || ledgerLocked.signum() > 0
                || ledgerOfflinePayPending.signum() > 0;
        if (ledgerHasOfflinePayFootprint && ledgerAvailable.compareTo(available) < 0) {
            return ledgerAvailable;
        }
        return available;
    }
}
