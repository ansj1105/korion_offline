package io.korion.offlinepay.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.service.SimpleCircuitBreaker;
import io.korion.offlinepay.application.service.TelegramAlertService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class CircuitBreakingCoinManageSettlementAdapterTest {

    @Test
    void clientContractFailureDoesNotOpenCircuit() {
        SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker(1, 10_000L);
        TelegramAlertService alertService = Mockito.mock(TelegramAlertService.class);
        RuntimeException badRequest = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "{\"error\":{\"code\":\"VALIDATION_ERROR\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        CircuitBreakingCoinManageSettlementAdapter adapter = new CircuitBreakingCoinManageSettlementAdapter(
                throwingSettlementPort(badRequest),
                circuitBreaker,
                alertService
        );

        assertThrows(HttpClientErrorException.class, () -> adapter.finalizeSettlement(command()));

        assertFalse(circuitBreaker.isOpen());
        verify(alertService, never()).notifyCircuitOpened(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void serverRuntimeFailureStillOpensCircuit() {
        SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker(1, 10_000L);
        TelegramAlertService alertService = Mockito.mock(TelegramAlertService.class);
        CircuitBreakingCoinManageSettlementAdapter adapter = new CircuitBreakingCoinManageSettlementAdapter(
                throwingSettlementPort(new IllegalStateException("coin_manage unavailable")),
                circuitBreaker,
                alertService
        );

        assertThrows(IllegalStateException.class, () -> adapter.finalizeSettlement(command()));

        assertTrue(circuitBreaker.isOpen());
        verify(alertService).notifyCircuitOpened("offline_pay -> coin_manage", "coin_manage unavailable");
    }

    private CoinManageSettlementPort throwingSettlementPort(RuntimeException exception) {
        return new CoinManageSettlementPort() {
            @Override
            public SettlementLedgerResult finalizeSettlement(SettlementLedgerCommand command) {
                throw exception;
            }

            @Override
            public SettlementLedgerResult compensateSettlement(SettlementCompensationCommand command) {
                throw exception;
            }

            @Override
            public PendingBalanceResult getOfflinePayPendingBalance(long userId, String assetCode) {
                throw exception;
            }
        };
    }

    private CoinManageSettlementPort.SettlementLedgerCommand command() {
        return new CoinManageSettlementPort.SettlementLedgerCommand(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                77L,
                "device-1",
                null,
                null,
                false,
                "KORI",
                new BigDecimal("1.000000"),
                BigDecimal.ZERO,
                "FAILED",
                "ADJUST",
                false,
                "0".repeat(64),
                "new-state",
                "previous-state",
                1L,
                "nonce-1",
                "signature-1"
        );
    }
}
