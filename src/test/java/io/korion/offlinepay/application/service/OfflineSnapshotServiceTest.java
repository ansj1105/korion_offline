package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfflineSnapshotServiceTest {

    @Test
    void currentSnapshotUsesRemainingAmountAsCurrentOnlineCollateralBalance() {
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        CollateralRepository collateralRepository = mock(CollateralRepository.class);
        IssuedOfflineProofRepository issuedOfflineProofRepository = mock(IssuedOfflineProofRepository.class);
        FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort = mock(FoxCoinWalletSnapshotPort.class);
        CoinManageCollateralPort coinManageCollateralPort = mock(CoinManageCollateralPort.class);

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
        when(coinManageCollateralPort.getBalanceSnapshot(1L, "KORI"))
                .thenReturn(new CoinManageCollateralPort.BalanceSnapshot("999.000000", "0.000000", "0.000000"));
        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        OfflineSnapshotService service = new OfflineSnapshotService(
                deviceRepository,
                collateralRepository,
                issuedOfflineProofRepository,
                mock(OfflinePaymentProofRepository.class),
                foxCoinWalletSnapshotPort,
                coinManageCollateralPort,
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
                jsonService,
                new JsonPayloadCanonicalizationService(jsonService),
                new ProofIssuerSignatureService(new AppProperties(
                        "KORI",
                        24,
                        20,
                        1000,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );

        OfflineSnapshotService.CurrentSnapshot snapshot = service.getCurrentSnapshot(1L, "device-1", "KORI");

        assertNotNull(snapshot.collateral());
        assertEquals("device-1", snapshot.collateral().deviceId());
        assertNotNull(snapshot.wallet());
        assertEquals("76.00000000", snapshot.collateral().lockedAmount());
        assertEquals("23.00000000", snapshot.collateral().remainingAmount());
        assertEquals("23.00000000", snapshot.collateral().collateralTotal());
        assertEquals("0", snapshot.collateral().unsettledOutgoing());
        assertEquals("23.00000000", snapshot.collateral().availableForPay());
        assertEquals("122.253587460317457206", snapshot.wallet().additionalCollateralAvailableAmount());

        OfflineSnapshotService.CurrentSnapshot localizedSnapshot = service.getCurrentSnapshot(
                1L,
                "device-1",
                "KORI",
                "Asia/Seoul",
                540
        );
        assertEquals("UTC", localizedSnapshot.serverTimeZone());
        assertEquals("Asia/Seoul", localizedSnapshot.clientTimeZone());
        assertEquals(540, localizedSnapshot.clientTimeZoneOffsetMinutes());
    }

    @Test
    void currentSnapshotDoesNotSubtractCollateralAgainWhenFoxyaSnapshotAlreadyExcludesOfflineCollateral() {
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        CollateralRepository collateralRepository = mock(CollateralRepository.class);
        IssuedOfflineProofRepository issuedOfflineProofRepository = mock(IssuedOfflineProofRepository.class);
        FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort = mock(FoxCoinWalletSnapshotPort.class);
        CoinManageCollateralPort coinManageCollateralPort = mock(CoinManageCollateralPort.class);

        when(deviceRepository.findByUserIdAndDeviceId(175L, "device-175")).thenReturn(Optional.empty());
        when(issuedOfflineProofRepository.findLatestActiveByUserIdAndDeviceIdAndAssetCode(175L, "device-175", "KORI"))
                .thenReturn(Optional.empty());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(175L, "KORI"))
                .thenReturn(Optional.of(new CollateralLock(
                        "collateral-175",
                        175L,
                        "AGGREGATED",
                        "KORI",
                        new BigDecimal("298.88400000"),
                        new BigDecimal("211.88400000"),
                        "AGGREGATED",
                        1,
                        CollateralStatus.LOCKED,
                        "lock-175",
                        OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                        "{}",
                        OffsetDateTime.parse("2026-05-31T00:00:00Z"),
                        OffsetDateTime.parse("2026-05-31T10:00:00Z")
                )));
        when(foxCoinWalletSnapshotPort.getCanonicalWalletSnapshot(175L, "KORI"))
                .thenReturn(new FoxCoinWalletSnapshotPort.WalletSnapshot(
                        175L,
                        "KORI",
                        new BigDecimal("291.614611"),
                        BigDecimal.ZERO,
                        "FOX_CLIENT_VISIBLE_AVAILABLE_KORI_EXCLUDING_OFFLINE_COLLATERAL",
                        "2026-05-31T10:00:00Z"
                ));
        when(coinManageCollateralPort.getBalanceSnapshot(175L, "KORI"))
                .thenReturn(new CoinManageCollateralPort.BalanceSnapshot("3.296700", "0.000000", "0.000000"));
        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        OfflineSnapshotService service = new OfflineSnapshotService(
                deviceRepository,
                collateralRepository,
                issuedOfflineProofRepository,
                mock(OfflinePaymentProofRepository.class),
                foxCoinWalletSnapshotPort,
                coinManageCollateralPort,
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
                jsonService,
                new JsonPayloadCanonicalizationService(jsonService),
                new ProofIssuerSignatureService(new AppProperties(
                        "KORI",
                        24,
                        20,
                        1000,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );

        OfflineSnapshotService.CurrentSnapshot snapshot = service.getCurrentSnapshot(175L, "device-175", "KORI");

        assertNotNull(snapshot.wallet());
        assertEquals("3.296700", snapshot.wallet().additionalCollateralAvailableAmount());
    }

    @Test
    void currentSnapshotDoesNotExposeProofFromStaleCollateral() {
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        CollateralRepository collateralRepository = mock(CollateralRepository.class);
        IssuedOfflineProofRepository issuedOfflineProofRepository = mock(IssuedOfflineProofRepository.class);
        FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort = mock(FoxCoinWalletSnapshotPort.class);
        CoinManageCollateralPort coinManageCollateralPort = mock(CoinManageCollateralPort.class);
        ProofIssuerSignatureService proofIssuerSignatureService = mock(ProofIssuerSignatureService.class);

        when(deviceRepository.findByUserIdAndDeviceId(39L, "device-39"))
                .thenReturn(Optional.of(new Device(
                        "registration-39",
                        "device-39",
                        39L,
                        "public-key",
                        1,
                        DeviceStatus.ACTIVE,
                        "{}",
                        OffsetDateTime.parse("2026-06-11T19:00:00Z"),
                        OffsetDateTime.parse("2026-06-11T19:00:00Z")
                )));
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI"))
                .thenReturn(Optional.of(new CollateralLock(
                        "aggregate-collateral",
                        39L,
                        "AGGREGATED",
                        "KORI",
                        new BigDecimal("10.00000000"),
                        new BigDecimal("10.00000000"),
                        "AGGREGATED",
                        1,
                        CollateralStatus.LOCKED,
                        "lock-39",
                        OffsetDateTime.parse("2026-06-12T21:27:20Z"),
                        "{}",
                        OffsetDateTime.parse("2026-06-11T21:27:20Z"),
                        OffsetDateTime.parse("2026-06-11T21:27:20Z")
                )));
        when(collateralRepository.findActiveByUserIdAndAssetCode(39L, "KORI"))
                .thenReturn(List.of(new CollateralLock(
                        "active-collateral",
                        39L,
                        "device-39",
                        "KORI",
                        new BigDecimal("10.00000000"),
                        new BigDecimal("10.00000000"),
                        "ACTIVE_ROOT",
                        1,
                        CollateralStatus.LOCKED,
                        "active-lock",
                        OffsetDateTime.parse("2026-06-12T21:27:20Z"),
                        "{}",
                        OffsetDateTime.parse("2026-06-11T21:27:20Z"),
                        OffsetDateTime.parse("2026-06-11T21:27:20Z")
                )));
        when(issuedOfflineProofRepository.findLatestActiveByUserIdAndDeviceIdAndAssetCode(39L, "device-39", "KORI"))
                .thenReturn(Optional.of(new IssuedOfflineProof(
                        "proof-stale",
                        39L,
                        "device-39",
                        "expired-collateral",
                        "KORI",
                        new BigDecimal("22.00000000"),
                        "proof_nonce",
                        "issuer-key",
                        "issuer-public-key",
                        "issuer-signature",
                        "{}",
                        IssuedProofStatus.ACTIVE,
                        null,
                        OffsetDateTime.parse("2026-06-12T20:27:39Z"),
                        OffsetDateTime.parse("2026-06-11T20:27:39Z"),
                        OffsetDateTime.parse("2026-06-11T20:27:39Z")
                )));
        when(proofIssuerSignatureService.verify(anyString(), anyString(), anyString())).thenReturn(true);
        when(foxCoinWalletSnapshotPort.getCanonicalWalletSnapshot(39L, "KORI"))
                .thenReturn(new FoxCoinWalletSnapshotPort.WalletSnapshot(
                        39L,
                        "KORI",
                        new BigDecimal("105.807278"),
                        BigDecimal.ZERO,
                        "FOX_CLIENT_VISIBLE_AVAILABLE_KORI_EXCLUDING_OFFLINE_COLLATERAL",
                        "2026-06-11T21:42:05Z"
                ));
        when(coinManageCollateralPort.getBalanceSnapshot(39L, "KORI"))
                .thenReturn(new CoinManageCollateralPort.BalanceSnapshot("999.000000", "0.000000", "0.000000"));

        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        OfflineSnapshotService service = new OfflineSnapshotService(
                deviceRepository,
                collateralRepository,
                issuedOfflineProofRepository,
                mock(OfflinePaymentProofRepository.class),
                foxCoinWalletSnapshotPort,
                coinManageCollateralPort,
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
                jsonService,
                new JsonPayloadCanonicalizationService(jsonService),
                proofIssuerSignatureService
        );

        OfflineSnapshotService.CurrentSnapshot snapshot = service.getCurrentSnapshot(39L, "device-39", "KORI");

        assertNotNull(snapshot.collateral());
        assertEquals("10.00000000", snapshot.collateral().availableForPay());
        assertNull(snapshot.issuedProof());
    }
}
