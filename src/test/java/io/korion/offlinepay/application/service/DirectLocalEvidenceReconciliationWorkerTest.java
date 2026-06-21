package io.korion.offlinepay.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DirectLocalEvidenceReconciliationWorkerTest {

    private final SettlementApplicationService settlementApplicationService = Mockito.mock(SettlementApplicationService.class);
    private final OfflineSagaService offlineSagaService = Mockito.mock(OfflineSagaService.class);

    @Test
    void pollReconcilesDirectLocalEvidenceWhenWorkerEnabled() {
        DirectLocalEvidenceReconciliationWorker worker = new DirectLocalEvidenceReconciliationWorker(
                settlementApplicationService,
                offlineSagaService,
                properties(true, 7)
        );
        when(settlementApplicationService.reconcileDirectLocalEvidence(7))
                .thenReturn(new SettlementApplicationService.DirectLocalEvidenceReconcileResult(1, 1, 0, 1, 0, 0, List.of("batch-1"), List.of("settlement-1")));
        when(offlineSagaService.findReceiverHistoryPendingStale(any(), anyInt()))
                .thenReturn(List.of());

        worker.poll();

        verify(settlementApplicationService).reconcileDirectLocalEvidence(7);
    }

    @Test
    void pollSkipsWhenWorkerDisabled() {
        DirectLocalEvidenceReconciliationWorker worker = new DirectLocalEvidenceReconciliationWorker(
                settlementApplicationService,
                offlineSagaService,
                properties(false, 7)
        );

        worker.poll();

        verify(settlementApplicationService, never()).reconcileDirectLocalEvidence(7);
        verify(offlineSagaService, never()).findReceiverHistoryPendingStale(any(), anyInt());
    }

    private AppProperties properties(boolean enabled, int localEvidenceReconciliationLimit) {
        return new AppProperties(
                "KORI",
                24,
                20,
                1000,
                new AppProperties.ProofIssuer("issuer", "", ""),
                new AppProperties.CoinManage("http://localhost", "key", 5000),
                new AppProperties.FoxCoin("http://localhost", "key", 5000),
                new AppProperties.Alerts(
                        new AppProperties.Telegram("", ""),
                        new AppProperties.CircuitBreaker(3, 60_000L)
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
                new AppProperties.Worker(
                        enabled,
                        "worker",
                        60_000,
                        3,
                        86_400_000L,
                        20,
                        localEvidenceReconciliationLimit,
                        300_000L
                )
        );
    }
}
