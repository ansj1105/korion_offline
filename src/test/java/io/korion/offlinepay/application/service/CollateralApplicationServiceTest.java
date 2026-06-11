package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CollateralApplicationServiceTest {

    private final CollateralRepository collateralRepository = mock(CollateralRepository.class);
    private final CollateralOperationRepository collateralOperationRepository = mock(CollateralOperationRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final SettlementBatchEventBus settlementBatchEventBus = mock(SettlementBatchEventBus.class);
    private final OfflineSagaService offlineSagaService = mock(OfflineSagaService.class);

    private final CollateralApplicationService service = new CollateralApplicationService(
            deviceRepository,
            collateralRepository,
            collateralOperationRepository,
            mock(FoxCoinWalletSnapshotPort.class),
            settlementBatchEventBus,
            offlineSagaService,
            new JsonService(new com.fasterxml.jackson.databind.ObjectMapper()),
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
            )
    );

    @Test
    void releaseCollateralUsesUserRemainingCollateralPoolForActiveSecurityDevice() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-08T00:00:00Z");
        CollateralLock requestedCollateral = new CollateralLock(
                "13a46c26-96fc-4375-85cc-58b55c8229df",
                1L,
                "old-device",
                "KORI",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "GENESIS",
                1,
                CollateralStatus.PARTIALLY_SETTLED,
                "topup:device-1:key",
                now.plusHours(1),
                "{}",
                now,
                now
        );
        CollateralLock activeCollateral = new CollateralLock(
                "23a46c26-96fc-4375-85cc-58b55c8229df",
                1L,
                "old-device",
                "KORI",
                new BigDecimal("10.000000"),
                new BigDecimal("10.000000"),
                "GENESIS",
                1,
                CollateralStatus.PARTIALLY_SETTLED,
                "topup:device-1:key",
                now.plusHours(1),
                "{}",
                now,
                now
        );
        CollateralLock aggregate = new CollateralLock(
                "aggregate",
                1L,
                "AGGREGATED",
                "KORI",
                new BigDecimal("10.000000"),
                new BigDecimal("10.000000"),
                "AGGREGATED",
                1,
                CollateralStatus.LOCKED,
                "topup:device-1:key",
                now.plusHours(1),
                "{}",
                now,
                now
        );
        CollateralOperation operation = new CollateralOperation(
                "11111111-1111-1111-1111-111111111111",
                activeCollateral.id(),
                1L,
                "device-1",
                "KORI",
                CollateralOperationType.RELEASE,
                new BigDecimal("6.000000"),
                CollateralOperationStatus.REQUESTED,
                "release:23a46c26-96f:test",
                null,
                "{}",
                now,
                now
        );

        when(deviceRepository.findByUserIdAndDeviceId(1L, "device-1")).thenReturn(Optional.of(activeDevice(now)));
        when(collateralRepository.findById(requestedCollateral.id())).thenReturn(Optional.of(requestedCollateral));
        when(collateralRepository.findAggregateByUserIdAndAssetCode(1L, "KORI")).thenReturn(Optional.of(aggregate));
        when(collateralRepository.findActiveByUserIdAndAssetCode(1L, "KORI")).thenReturn(List.of(activeCollateral));
        when(collateralOperationRepository.saveRequested(
                eq(activeCollateral.id()),
                eq(1L),
                eq("device-1"),
                eq("KORI"),
                eq(CollateralOperationType.RELEASE),
                eq(new BigDecimal("6.000000")),
                anyString(),
                anyString()
        )).thenReturn(operation);

        service.releaseCollateral(
                requestedCollateral.id(),
                new CollateralApplicationService.ReleaseCollateralCommand(
                        1L,
                        "device-1",
                        new BigDecimal("6.000000"),
                        "manual_release",
                        Map.of(),
                        "test"
                )
        );

        verify(collateralOperationRepository).saveRequested(
                eq(activeCollateral.id()),
                eq(1L),
                eq("device-1"),
                eq("KORI"),
                eq(CollateralOperationType.RELEASE),
                eq(new BigDecimal("6.000000")),
                anyString(),
                anyString()
        );
        verify(settlementBatchEventBus).publishCollateralOperationRequested(
                eq(operation.id()),
                eq("RELEASE"),
                eq("KORI"),
                eq(operation.referenceId()),
                anyString()
        );
    }

    private Device activeDevice(OffsetDateTime now) {
        return new Device(
                "device-row-1",
                "device-1",
                1L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                now,
                now
        );
    }

    @Test
    void buildReleaseReferenceIdKeepsReferenceWithinCoinManageLimit() throws Exception {
        Method method = CollateralApplicationService.class.getDeclaredMethod(
                "buildReleaseReferenceId",
                String.class,
                CollateralApplicationService.ReleaseCollateralCommand.class
        );
        method.setAccessible(true);

        String referenceId = (String) method.invoke(
                service,
                "13a46c26-96fc-4375-85cc-58b55c8229df",
                new CollateralApplicationService.ReleaseCollateralCommand(
                        1L,
                        "99598a7b-03ba-45e3-8e22-164d17a52436",
                        java.math.BigDecimal.ONE,
                        "manual_release",
                        java.util.Map.of(),
                        "collateral_release_1774423546711"
                )
        );

        assertTrue(referenceId.length() <= 64, "referenceId must fit coin_manage varchar(64)");
        assertTrue(referenceId.startsWith("release:"));
    }

    @Test
    void buildTopupReferenceIdKeepsReferenceWithinCoinManageLimit() throws Exception {
        Method method = CollateralApplicationService.class.getDeclaredMethod(
                "buildTopupReferenceId",
                CollateralApplicationService.CreateCollateralCommand.class
        );
        method.setAccessible(true);

        String referenceId = (String) method.invoke(
                service,
                new CollateralApplicationService.CreateCollateralCommand(
                        1L,
                        "99598a7b-03ba-45e3-8e22-164d17a52436",
                        java.math.BigDecimal.TEN,
                        "KORI",
                        "GENESIS",
                        1,
                        java.util.Map.of(),
                        "some-very-long-idempotency-key-for-topup-flow-that-must-not-overflow-ledger-reference"
                )
        );

        assertTrue(referenceId.length() <= 64, "referenceId must fit coin_manage varchar(64)");
        assertTrue(referenceId.startsWith("topup:"));
    }
}
