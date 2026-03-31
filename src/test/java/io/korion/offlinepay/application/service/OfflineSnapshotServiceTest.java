package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.CollateralStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfflineSnapshotServiceTest {

    @Test
    void currentSnapshotUsesLockedAmountForAggregateCollateralAndAvailableBalance() {
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        CollateralRepository collateralRepository = mock(CollateralRepository.class);
        IssuedOfflineProofRepository issuedOfflineProofRepository = mock(IssuedOfflineProofRepository.class);
        FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort = mock(FoxCoinWalletSnapshotPort.class);

        when(deviceRepository.findByUserIdAndDeviceId(1L, "device-1")).thenReturn(Optional.empty());
        when(issuedOfflineProofRepository.findLatestActiveByUserIdAndDeviceIdAndAssetCode(1L, "device-1", "KORI"))
                .thenReturn(Optional.empty());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(1L, "KORI"))
                .thenReturn(Optional.of(new CollateralLock(
                        "collateral-1",
                        1L,
                        "AGGREGATED",
                        "KORI",
                        new BigDecimal("76.00000000"),
                        new BigDecimal("23.00000000"),
                        "AGGREGATED",
                        1,
                        CollateralStatus.LOCKED,
                        "lock-1",
                        OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                        "{}",
                        OffsetDateTime.parse("2026-03-26T00:00:00Z"),
                        OffsetDateTime.parse("2026-03-31T00:00:00Z")
                )));
        when(foxCoinWalletSnapshotPort.getCanonicalWalletSnapshot(1L, "KORI"))
                .thenReturn(new FoxCoinWalletSnapshotPort.WalletSnapshot(
                        1L,
                        "KORI",
                        new BigDecimal("198.253587460317457206"),
                        new BigDecimal("1.001000000000000000"),
                        "FOX_CLIENT_VISIBLE_TOTAL_KORI",
                        "2026-03-31T00:00:00Z"
                ));

        OfflineSnapshotService service = new OfflineSnapshotService(
                deviceRepository,
                collateralRepository,
                issuedOfflineProofRepository,
                foxCoinWalletSnapshotPort,
                new AppProperties(
                        "KORI",
                        24,
                        20,
                        1000,
                        null,
                        new AppProperties.CoinManage("http://localhost:3000", "secret", 5000),
                        new AppProperties.FoxCoin("http://localhost:8080", "secret", 5000),
                        null,
                        null,
                        new AppProperties.Worker(false, "worker", 60000, 3)
                ),
                new JsonService(new com.fasterxml.jackson.databind.ObjectMapper())
        );

        OfflineSnapshotService.CurrentSnapshot snapshot = service.getCurrentSnapshot(1L, "device-1", "KORI");

        assertNotNull(snapshot.collateral());
        assertNotNull(snapshot.wallet());
        assertEquals("76.00000000", snapshot.collateral().lockedAmount());
        assertEquals("23.00000000", snapshot.collateral().remainingAmount());
        assertEquals("122.253587460317457206", snapshot.wallet().additionalCollateralAvailableAmount());
    }
}
