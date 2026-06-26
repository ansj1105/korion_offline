package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CollateralAvailabilityCalculatorTest {

    @Test
    void subtractsCurrentCollateralEvenWhenSnapshotBasisSaysExcludingOfflineCollateral() {
        BigDecimal available = CollateralAvailabilityCalculator.resolveAdditionalCollateralAvailableAmount(
                new FoxCoinWalletSnapshotPort.WalletSnapshot(
                        175L,
                        "KORI",
                        new BigDecimal("291.614611"),
                        BigDecimal.ZERO,
                        "FOX_CLIENT_VISIBLE_AVAILABLE_KORI_EXCLUDING_OFFLINE_COLLATERAL",
                        "2026-05-31T10:00:00Z"
                ),
                new BigDecimal("298.884000")
        );

        assertEquals(BigDecimal.ZERO, available);
    }

    @Test
    void subtractsCurrentCollateralWhenSnapshotIsRawTotalBalance() {
        BigDecimal available = CollateralAvailabilityCalculator.resolveAdditionalCollateralAvailableAmount(
                new FoxCoinWalletSnapshotPort.WalletSnapshot(
                        1L,
                        "KORI",
                        new BigDecimal("198.253587460317457206"),
                        BigDecimal.ZERO,
                        "FOX_CLIENT_VISIBLE_TOTAL_KORI",
                        "2026-05-31T10:00:00Z"
                ),
                new BigDecimal("76.00000000")
        );

        assertEquals(new BigDecimal("122.253587460317457206"), available);
    }
}
