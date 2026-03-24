package io.korion.offlinepay.application.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SettlementWorkerTest {

    private final SettlementBatchEventBus eventBus = Mockito.mock(SettlementBatchEventBus.class);
    private final SettlementApplicationService settlementApplicationService = Mockito.mock(SettlementApplicationService.class);

    @Test
    void deadLettersBatchAfterMaxAttempts() {
        AppProperties properties = new AppProperties(
                "USDT",
                24,
                20,
                1000,
                new AppProperties.ProofIssuer("test-proof-issuer", "", ""),
                new AppProperties.CoinManage("http://localhost:3000", "test-key", 5000),
                new AppProperties.FoxCoin("http://localhost:3101", "test-key", 5000),
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
                new AppProperties.Worker(true, "worker-1", 60000, 3)
        );
        SettlementWorker worker = new SettlementWorker(eventBus, settlementApplicationService, properties);
        SettlementBatchEventBus.QueuedBatchMessage message =
                new SettlementBatchEventBus.QueuedBatchMessage("1-0", "batch-1", "SENDER", "device-1");

        when(eventBus.pollRequestedBatches(20)).thenReturn(List.of(message));
        when(eventBus.reclaimStaleRequestedBatches(20, 60000)).thenReturn(List.of());
        Mockito.doThrow(new IllegalStateException("boom"))
                .when(settlementApplicationService)
                .markBatchValidating("batch-1");
        when(settlementApplicationService.recordBatchProcessingFailure("batch-1", "boom", 3))
                .thenReturn(new SettlementApplicationService.BatchFailureOutcome("batch-1", 3, true));

        worker.poll();

        verify(eventBus).publishDeadLetter(eq("batch-1"), eq(3), eq("boom"), anyString());
        verify(eventBus).acknowledgeRequested("1-0");
        verify(settlementApplicationService, never()).finalizeBatch("batch-1");
    }

    @Test
    void deadLettersExternalSyncAfterMaxAttempts() {
        AppProperties properties = new AppProperties(
                "USDT",
                24,
                20,
                1000,
                new AppProperties.ProofIssuer("test-proof-issuer", "", ""),
                new AppProperties.CoinManage("http://localhost:3000", "test-key", 5000),
                new AppProperties.FoxCoin("http://localhost:3101", "test-key", 5000),
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
                new AppProperties.Worker(true, "worker-1", 60000, 3)
        );
        io.korion.offlinepay.application.port.CoinManageSettlementPort coinManageSettlementPort =
                Mockito.mock(io.korion.offlinepay.application.port.CoinManageSettlementPort.class);
        io.korion.offlinepay.application.port.FoxCoinHistoryPort foxCoinHistoryPort =
                Mockito.mock(io.korion.offlinepay.application.port.FoxCoinHistoryPort.class);
        io.korion.offlinepay.application.port.ReconciliationCaseRepository reconciliationCaseRepository =
                Mockito.mock(io.korion.offlinepay.application.port.ReconciliationCaseRepository.class);
        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        SettlementExternalSyncWorker worker = new SettlementExternalSyncWorker(
                eventBus,
                coinManageSettlementPort,
                foxCoinHistoryPort,
                reconciliationCaseRepository,
                jsonService,
                properties
        );
        SettlementBatchEventBus.QueuedExternalSyncMessage message =
                new SettlementBatchEventBus.QueuedExternalSyncMessage(
                        "sync-1",
                        "LEDGER_SYNC_REQUESTED",
                        "settlement-1",
                        "batch-1",
                        "proof-1",
                        "{\"ledgerCommand\":{\"settlementId\":\"settlement-1\",\"batchId\":\"batch-1\",\"collateralId\":\"collateral-1\",\"proofId\":\"proof-1\",\"userId\":1,\"deviceId\":\"device-1\",\"assetCode\":\"USDT\",\"amount\":10,\"settlementStatus\":\"SETTLED\",\"releaseAction\":\"RELEASE\",\"conflictDetected\":false,\"proofFingerprint\":\"fp\",\"newStateHash\":\"hash\",\"previousHash\":\"prev\",\"monotonicCounter\":1,\"nonce\":\"nonce\",\"signature\":\"sig\"},\"historyCommand\":{\"settlementId\":\"settlement-1\",\"batchId\":\"batch-1\",\"collateralId\":\"collateral-1\",\"proofId\":\"proof-1\",\"userId\":1,\"deviceId\":\"device-1\",\"assetCode\":\"USDT\",\"amount\":10,\"settlementStatus\":\"SETTLED\",\"historyType\":\"OFFLINE_PAY_SETTLEMENT\"}}",
                        3
                );

        when(eventBus.pollExternalSyncRequested(20)).thenReturn(List.of(message));
        when(eventBus.reclaimStaleExternalSyncRequested(20, 60000)).thenReturn(List.of());
        Mockito.doThrow(new IllegalStateException("boom"))
                .when(coinManageSettlementPort)
                .finalizeSettlement(Mockito.any(io.korion.offlinepay.application.port.CoinManageSettlementPort.SettlementLedgerCommand.class));

        worker.poll();

        verify(eventBus).publishExternalSyncDeadLetter(
                eq("LEDGER_SYNC_REQUESTED"),
                eq("settlement-1"),
                eq("batch-1"),
                eq("proof-1"),
                eq(3),
                eq("LEDGER_SYNC_FAIL"),
                eq("boom"),
                anyString()
        );
        verify(eventBus).acknowledgeExternalSync("sync-1");
    }
}
