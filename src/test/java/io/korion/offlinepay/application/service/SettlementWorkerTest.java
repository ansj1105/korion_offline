package io.korion.offlinepay.application.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
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
        OfflineSagaService offlineSagaService = Mockito.mock(OfflineSagaService.class);
        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        SettlementExternalSyncWorker worker = new SettlementExternalSyncWorker(
                eventBus,
                coinManageSettlementPort,
                foxCoinHistoryPort,
                reconciliationCaseRepository,
                offlineSagaService,
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

    @Test
    void externalSyncWorkerStoresLedgerResultInSagaPayload() {
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
        OfflineSagaService offlineSagaService = Mockito.mock(OfflineSagaService.class);
        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        SettlementExternalSyncWorker worker = new SettlementExternalSyncWorker(
                eventBus,
                coinManageSettlementPort,
                foxCoinHistoryPort,
                reconciliationCaseRepository,
                offlineSagaService,
                jsonService,
                properties
        );
        SettlementBatchEventBus.QueuedExternalSyncMessage message =
                new SettlementBatchEventBus.QueuedExternalSyncMessage(
                        "sync-2",
                        "LEDGER_SYNC_REQUESTED",
                        "settlement-1",
                        "batch-1",
                        "proof-1",
                        "{\"ledgerCommand\":{\"settlementId\":\"settlement-1\",\"batchId\":\"batch-1\",\"collateralId\":\"collateral-1\",\"proofId\":\"proof-1\",\"userId\":1,\"deviceId\":\"device-1\",\"assetCode\":\"USDT\",\"amount\":10,\"settlementStatus\":\"SETTLED\",\"releaseAction\":\"RELEASE\",\"conflictDetected\":false,\"proofFingerprint\":\"fp\",\"newStateHash\":\"hash\",\"previousHash\":\"prev\",\"monotonicCounter\":1,\"nonce\":\"nonce\",\"signature\":\"sig\"},\"historyCommand\":{\"settlementId\":\"settlement-1\",\"batchId\":\"batch-1\",\"collateralId\":\"collateral-1\",\"proofId\":\"proof-1\",\"userId\":1,\"deviceId\":\"device-1\",\"assetCode\":\"USDT\",\"amount\":10,\"settlementStatus\":\"SETTLED\",\"historyType\":\"OFFLINE_PAY_SETTLEMENT\"}}",
                        1
                );

        when(eventBus.pollExternalSyncRequested(20)).thenReturn(List.of(message));
        when(eventBus.reclaimStaleExternalSyncRequested(20, 60000)).thenReturn(List.of());
        when(coinManageSettlementPort.finalizeSettlement(Mockito.any(io.korion.offlinepay.application.port.CoinManageSettlementPort.SettlementLedgerCommand.class)))
                .thenReturn(new io.korion.offlinepay.application.port.CoinManageSettlementPort.SettlementLedgerResult(
                        "settlement-1",
                        "FINALIZED",
                        "RELEASE",
                        false,
                        "SENDER",
                        "EXTERNAL_HISTORY_SYNC",
                        "SENDER_LEDGER_PLUS_RECEIVER_HISTORY",
                        "OFFLINE_PAY_SAGA",
                        new BigDecimal("10.000000"),
                        new BigDecimal("140.000000"),
                        new BigDecimal("140.000000")
                ));

        worker.poll();

        verify(offlineSagaService).markPartiallyApplied(
                eq(io.korion.offlinepay.domain.status.OfflineSagaType.SETTLEMENT),
                eq("settlement-1"),
                eq("LEDGER_SYNCED"),
                Mockito.argThat(payload -> payload != null
                        && payload.containsKey("ledgerResult")
                        && payload.get("ledgerResult").toString().contains("FINALIZED")
                        && payload.get("ledgerResult").toString().contains("EXTERNAL_HISTORY_SYNC"))
        );
    }

    @Test
    void reconciliationFollowUpWorkerRequeuesRetryableExternalSync() {
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
        io.korion.offlinepay.application.port.ReconciliationCaseRepository reconciliationCaseRepository =
                Mockito.mock(io.korion.offlinepay.application.port.ReconciliationCaseRepository.class);
        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        ReconciliationFollowUpWorker worker = new ReconciliationFollowUpWorker(
                reconciliationCaseRepository,
                eventBus,
                jsonService,
                properties
        );
        ReconciliationCase reconciliationCase = new ReconciliationCase(
                "case-1",
                "settlement-1",
                "batch-1",
                "proof-1",
                "voucher-1",
                "LEDGER_SYNC_FAILED",
                ReconciliationCaseStatus.OPEN,
                "LEDGER_SYNC_FAIL",
                "{\"retryable\":true,\"nextAction\":\"RETRY_EXTERNAL_SYNC\",\"eventType\":\"LEDGER_SYNC_REQUESTED\",\"payloadJson\":\"{\\\"ledgerCommand\\\":{\\\"settlementId\\\":\\\"settlement-1\\\",\\\"batchId\\\":\\\"batch-1\\\",\\\"collateralId\\\":\\\"collateral-1\\\",\\\"proofId\\\":\\\"proof-1\\\",\\\"userId\\\":1,\\\"deviceId\\\":\\\"device-1\\\",\\\"assetCode\\\":\\\"USDT\\\",\\\"amount\\\":10,\\\"settlementStatus\\\":\\\"SETTLED\\\",\\\"releaseAction\\\":\\\"RELEASE\\\",\\\"conflictDetected\\\":false,\\\"proofFingerprint\\\":\\\"fp\\\",\\\"newStateHash\\\":\\\"hash\\\",\\\"previousHash\\\":\\\"prev\\\",\\\"monotonicCounter\\\":1,\\\"nonce\\\":\\\"nonce\\\",\\\"signature\\\":\\\"sig\\\"},\\\"historyCommand\\\":{\\\"settlementId\\\":\\\"settlement-1\\\",\\\"batchId\\\":\\\"batch-1\\\",\\\"collateralId\\\":\\\"collateral-1\\\",\\\"proofId\\\":\\\"proof-1\\\",\\\"userId\\\":1,\\\"deviceId\\\":\\\"device-1\\\",\\\"assetCode\\\":\\\"USDT\\\",\\\"amount\\\":10,\\\"settlementStatus\\\":\\\"SETTLED\\\",\\\"historyType\\\":\\\"OFFLINE_PAY_SETTLEMENT\\\"}}\",\"nextRetryAt\":\"2020-01-01T00:00:00Z\",\"retryCount\":0}",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );
        when(reconciliationCaseRepository.findOpenRetryable(20)).thenReturn(List.of(reconciliationCase));

        worker.poll();

        verify(eventBus).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                eq("settlement-1"),
                eq("batch-1"),
                eq("proof-1"),
                anyString(),
                anyString()
        );
        verify(reconciliationCaseRepository).updateDetail(eq("case-1"), anyString());
    }

    @Test
    void reconciliationFollowUpWorkerRequeuesRetryableCollateralSync() {
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
        io.korion.offlinepay.application.port.ReconciliationCaseRepository reconciliationCaseRepository =
                Mockito.mock(io.korion.offlinepay.application.port.ReconciliationCaseRepository.class);
        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        ReconciliationFollowUpWorker worker = new ReconciliationFollowUpWorker(
                reconciliationCaseRepository,
                eventBus,
                jsonService,
                properties
        );
        ReconciliationCase reconciliationCase = new ReconciliationCase(
                "case-2",
                null,
                "11111111-1111-1111-1111-111111111111",
                null,
                null,
                "COLLATERAL_LOCK_FAILED",
                ReconciliationCaseStatus.OPEN,
                "COLLATERAL_LOCK_FAIL",
                "{\"retryable\":true,\"nextAction\":\"RETRY_COLLATERAL_SYNC\",\"operationId\":\"11111111-1111-1111-1111-111111111111\",\"operationType\":\"TOPUP\",\"assetCode\":\"USDT\",\"referenceId\":\"topup:device-1:123\",\"nextRetryAt\":\"2020-01-01T00:00:00Z\",\"retryCount\":0}",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );
        when(reconciliationCaseRepository.findOpenRetryable(20)).thenReturn(List.of(reconciliationCase));

        worker.poll();

        verify(eventBus).publishCollateralOperationRequested(
                eq("11111111-1111-1111-1111-111111111111"),
                eq("TOPUP"),
                eq("USDT"),
                eq("topup:device-1:123"),
                anyString()
        );
        verify(reconciliationCaseRepository).updateDetail(eq("case-2"), anyString());
    }

    @Test
    void collateralExternalSyncWorkerDeadLettersAfterMaxAttempts() {
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
        io.korion.offlinepay.application.port.CollateralOperationRepository collateralOperationRepository =
                Mockito.mock(io.korion.offlinepay.application.port.CollateralOperationRepository.class);
        io.korion.offlinepay.application.port.CollateralRepository collateralRepository =
                Mockito.mock(io.korion.offlinepay.application.port.CollateralRepository.class);
        io.korion.offlinepay.application.port.CoinManageCollateralPort coinManageCollateralPort =
                Mockito.mock(io.korion.offlinepay.application.port.CoinManageCollateralPort.class);
        io.korion.offlinepay.application.port.ReconciliationCaseRepository reconciliationCaseRepository =
                Mockito.mock(io.korion.offlinepay.application.port.ReconciliationCaseRepository.class);
        OfflineSnapshotStreamService snapshotStreamService = Mockito.mock(OfflineSnapshotStreamService.class);
        OfflineSagaService offlineSagaService = Mockito.mock(OfflineSagaService.class);
        TelegramAlertService telegramAlertService = Mockito.mock(TelegramAlertService.class);
        JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
        CollateralExternalSyncWorker worker = new CollateralExternalSyncWorker(
                eventBus,
                collateralOperationRepository,
                collateralRepository,
                coinManageCollateralPort,
                reconciliationCaseRepository,
                snapshotStreamService,
                offlineSagaService,
                jsonService,
                telegramAlertService,
                properties
        );
        SettlementBatchEventBus.QueuedCollateralMessage message =
                new SettlementBatchEventBus.QueuedCollateralMessage(
                        "collateral-1",
                        "11111111-1111-1111-1111-111111111111",
                        "TOPUP",
                        "USDT",
                        "topup:device-1:123",
                        3
                );
        CollateralOperation operation = new CollateralOperation(
                "11111111-1111-1111-1111-111111111111",
                null,
                1L,
                "device-1",
                "USDT",
                CollateralOperationType.TOPUP,
                java.math.BigDecimal.TEN,
                CollateralOperationStatus.REQUESTED,
                "topup:device-1:123",
                null,
                "{\"policyVersion\":1,\"initialStateRoot\":\"GENESIS\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(eventBus.pollCollateralOperationRequested(20)).thenReturn(List.of(message));
        when(eventBus.reclaimStaleCollateralOperationRequested(20, 60000)).thenReturn(List.of());
        when(collateralOperationRepository.findById("11111111-1111-1111-1111-111111111111")).thenReturn(java.util.Optional.of(operation));
        Mockito.doThrow(new IllegalStateException("boom"))
                .when(coinManageCollateralPort)
                .lockCollateral(eq(1L), eq("device-1"), eq("USDT"), eq(java.math.BigDecimal.TEN), eq("topup:device-1:123"), eq(1));

        worker.poll();

        verify(collateralOperationRepository).markFailed(eq("topup:device-1:123"), eq("boom"), anyString());
        verify(eventBus).publishCollateralOperationResult(
                eq("11111111-1111-1111-1111-111111111111"),
                eq("TOPUP"),
                eq("FAILED"),
                eq("USDT"),
                eq("topup:device-1:123"),
                anyString(),
                eq("boom"),
                eq("COLLATERAL_LOCK_FAIL")
        );
        verify(eventBus).acknowledgeCollateral("collateral-1");
    }
}
