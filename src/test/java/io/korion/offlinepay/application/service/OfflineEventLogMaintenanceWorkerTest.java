package io.korion.offlinepay.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OfflineEventLogMaintenanceWorkerTest {

    @Test
    void closesResolvedAndExpiredPendingEventsWhenWorkerEnabled() {
        OfflineEventLogRepository repository = Mockito.mock(OfflineEventLogRepository.class);
        OfflineEventLogMaintenanceWorker worker = new OfflineEventLogMaintenanceWorker(
                repository,
                properties(true)
        );

        worker.poll();

        verify(repository).closePendingResolvedByTerminalEvents();
        verify(repository).expirePendingOlderThan(any(), eq("REQUEST_TIMEOUT"));
    }

    @Test
    void skipsMaintenanceWhenWorkerDisabled() {
        OfflineEventLogRepository repository = Mockito.mock(OfflineEventLogRepository.class);
        OfflineEventLogMaintenanceWorker worker = new OfflineEventLogMaintenanceWorker(
                repository,
                properties(false)
        );

        worker.poll();

        verify(repository, never()).closePendingResolvedByTerminalEvents();
        verify(repository, never()).expirePendingOlderThan(any(), any());
    }

    private static AppProperties properties(boolean workerEnabled) {
        return new AppProperties(
                "KORI",
                24,
                10,
                1000,
                new AppProperties.ProofIssuer("issuer", "public", "private"),
                new AppProperties.CoinManage("http://coin-manage", "api-key", 5000),
                new AppProperties.FoxCoin("http://fox-coin", "api-key", 5000),
                new AppProperties.Alerts(
                        new AppProperties.Telegram("", ""),
                        new AppProperties.CircuitBreaker(3, 60000)
                ),
                new AppProperties.Redis(
                        "offlinepay",
                        "stream:settlement:requested",
                        "stream:settlement:result",
                        "stream:settlement:conflict",
                        "stream:settlement:dead-letter",
                        "stream:collateral:requested",
                        "stream:collateral:result",
                        "offlinepay:settlement-group"
                ),
                new AppProperties.Worker(workerEnabled, "worker", 60000, 3)
        );
    }
}
