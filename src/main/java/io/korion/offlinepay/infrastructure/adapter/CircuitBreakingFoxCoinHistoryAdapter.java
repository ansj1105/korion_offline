package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.service.SimpleCircuitBreaker;
import io.korion.offlinepay.application.service.TelegramAlertService;

public class CircuitBreakingFoxCoinHistoryAdapter implements FoxCoinHistoryPort {

    private final FoxCoinHistoryPort delegate;
    private final SimpleCircuitBreaker circuitBreaker;
    private final TelegramAlertService alertService;

    public CircuitBreakingFoxCoinHistoryAdapter(
            FoxCoinHistoryPort delegate,
            SimpleCircuitBreaker circuitBreaker,
            TelegramAlertService alertService
    ) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.alertService = alertService;
    }

    @Override
    public void recordSettlementHistory(SettlementHistoryCommand command) {
        try {
            circuitBreaker.assertCallable();
            delegate.recordSettlementHistory(command);
            if (circuitBreaker.onSuccess()) {
                alertService.notifyCircuitRecovered("offline_pay -> foxya");
            }
        } catch (RuntimeException exception) {
            boolean opened = circuitBreaker.onFailure();
            if (opened) {
                alertService.notifyCircuitOpened("offline_pay -> foxya", exception.getMessage());
            }
            throw exception;
        }
    }
}
