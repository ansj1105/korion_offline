package io.korion.offlinepay.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.service.SimpleCircuitBreaker;
import io.korion.offlinepay.application.service.TelegramAlertService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class CircuitBreakingFoxCoinHistoryAdapterTest {

    @Test
    void clientContractFailureDoesNotOpenCircuit() {
        SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker(1, 10_000L);
        TelegramAlertService alertService = Mockito.mock(TelegramAlertService.class);
        RuntimeException conflict = new HttpClientErrorException(
                HttpStatus.CONFLICT,
                "Conflict",
                "{\"status\":\"ERROR\",\"message\":\"Offline pay transfer reference conflict.\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        CircuitBreakingFoxCoinHistoryAdapter adapter = new CircuitBreakingFoxCoinHistoryAdapter(
                throwingHistoryPort(conflict),
                circuitBreaker,
                alertService
        );

        assertThrows(HttpClientErrorException.class, () -> adapter.recordSettlementHistory(command()));

        assertFalse(circuitBreaker.isOpen());
        verify(alertService, never()).notifyCircuitOpened(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void serverRuntimeFailureStillOpensCircuit() {
        SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker(1, 10_000L);
        TelegramAlertService alertService = Mockito.mock(TelegramAlertService.class);
        CircuitBreakingFoxCoinHistoryAdapter adapter = new CircuitBreakingFoxCoinHistoryAdapter(
                throwingHistoryPort(new IllegalStateException("foxya unavailable")),
                circuitBreaker,
                alertService
        );

        assertThrows(IllegalStateException.class, () -> adapter.recordSettlementHistory(command()));

        assertTrue(circuitBreaker.isOpen());
        verify(alertService).notifyCircuitOpened("offline_pay -> foxya", "foxya unavailable");
    }

    private FoxCoinHistoryPort throwingHistoryPort(RuntimeException exception) {
        return command -> {
            throw exception;
        };
    }

    private FoxCoinHistoryPort.SettlementHistoryCommand command() {
        return new FoxCoinHistoryPort.SettlementHistoryCommand(
                "settlement-1",
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                77L,
                "device-1",
                "KORI",
                new BigDecimal("1.000000"),
                BigDecimal.ZERO,
                "SETTLED",
                "OFFLINE_PAY_SETTLEMENT"
        );
    }
}
