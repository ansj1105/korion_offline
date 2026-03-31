package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.service.SimpleCircuitBreaker;
import io.korion.offlinepay.application.service.TelegramAlertService;
import java.math.BigDecimal;

public class CircuitBreakingCoinManageCollateralAdapter implements CoinManageCollateralPort {

    private final CoinManageCollateralPort delegate;
    private final SimpleCircuitBreaker circuitBreaker;
    private final TelegramAlertService alertService;

    public CircuitBreakingCoinManageCollateralAdapter(
            CoinManageCollateralPort delegate,
            SimpleCircuitBreaker circuitBreaker,
            TelegramAlertService alertService
    ) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.alertService = alertService;
    }

    @Override
    public LockCollateralResult lockCollateral(
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal amount,
            String referenceId,
            int policyVersion
    ) {
        try {
            circuitBreaker.assertCallable();
            LockCollateralResult result = delegate.lockCollateral(userId, deviceId, assetCode, amount, referenceId, policyVersion);
            if (circuitBreaker.onSuccess()) {
                alertService.notifyCircuitRecovered("offline_pay -> coin_manage collateral");
            }
            return result;
        } catch (RuntimeException exception) {
            boolean opened = circuitBreaker.onFailure();
            if (opened) {
                alertService.notifyCircuitOpened("offline_pay -> coin_manage collateral", exception.getMessage());
            }
            throw exception;
        }
    }

    @Override
    public ReleaseCollateralResult releaseCollateral(
            long userId,
            String deviceId,
            String collateralId,
            String assetCode,
            BigDecimal amount,
            String referenceId
    ) {
        try {
            circuitBreaker.assertCallable();
            ReleaseCollateralResult result = delegate.releaseCollateral(userId, deviceId, collateralId, assetCode, amount, referenceId);
            if (circuitBreaker.onSuccess()) {
                alertService.notifyCircuitRecovered("offline_pay -> coin_manage collateral");
            }
            return result;
        } catch (RuntimeException exception) {
            boolean opened = circuitBreaker.onFailure();
            if (opened) {
                alertService.notifyCircuitOpened("offline_pay -> coin_manage collateral", exception.getMessage());
            }
            throw exception;
        }
    }
}
