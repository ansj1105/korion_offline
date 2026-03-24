package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.service.SimpleCircuitBreaker;
import io.korion.offlinepay.application.service.TelegramAlertService;

public class CircuitBreakingCoinManageSettlementAdapter implements CoinManageSettlementPort {

    private final CoinManageSettlementPort delegate;
    private final SimpleCircuitBreaker circuitBreaker;
    private final TelegramAlertService alertService;

    public CircuitBreakingCoinManageSettlementAdapter(
            CoinManageSettlementPort delegate,
            SimpleCircuitBreaker circuitBreaker,
            TelegramAlertService alertService
    ) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.alertService = alertService;
    }

    @Override
    public void finalizeSettlement(SettlementLedgerCommand command) {
        try {
            circuitBreaker.assertCallable();
            delegate.finalizeSettlement(command);
            if (circuitBreaker.onSuccess()) {
                alertService.notifyCircuitRecovered("offline_pay -> coin_manage");
            }
        } catch (RuntimeException exception) {
            boolean opened = circuitBreaker.onFailure();
            if (opened) {
                alertService.notifyCircuitOpened("offline_pay -> coin_manage", exception.getMessage());
            }
            throw exception;
        }
    }

    @Override
    public void compensateSettlement(SettlementCompensationCommand command) {
        try {
            circuitBreaker.assertCallable();
            delegate.compensateSettlement(command);
            if (circuitBreaker.onSuccess()) {
                alertService.notifyCircuitRecovered("offline_pay -> coin_manage");
            }
        } catch (RuntimeException exception) {
            boolean opened = circuitBreaker.onFailure();
            if (opened) {
                alertService.notifyCircuitOpened("offline_pay -> coin_manage", exception.getMessage());
            }
            throw exception;
        }
    }
}
