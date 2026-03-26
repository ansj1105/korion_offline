package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class CollateralApplicationServiceTest {

    private final CollateralApplicationService service = new CollateralApplicationService(
            mock(DeviceRepository.class),
            mock(CollateralRepository.class),
            mock(CollateralOperationRepository.class),
            mock(FoxCoinWalletSnapshotPort.class),
            mock(SettlementBatchEventBus.class),
            mock(OfflineSagaService.class),
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
