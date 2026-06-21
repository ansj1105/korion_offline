package io.korion.offlinepay.application.service;

import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.OfflineSaga;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DirectLocalEvidenceReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(DirectLocalEvidenceReconciliationWorker.class);

    private final SettlementApplicationService settlementApplicationService;
    private final OfflineSagaService offlineSagaService;
    private final AppProperties properties;

    public DirectLocalEvidenceReconciliationWorker(
            SettlementApplicationService settlementApplicationService,
            OfflineSagaService offlineSagaService,
            AppProperties properties
    ) {
        this.settlementApplicationService = settlementApplicationService;
        this.offlineSagaService = offlineSagaService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.local-evidence-reconciliation-delay-ms:15000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }

        try {
            SettlementApplicationService.DirectLocalEvidenceReconcileResult result =
                    settlementApplicationService.reconcileDirectLocalEvidence(properties.worker().localEvidenceReconciliationLimit());
            if (result.candidates() > 0 || result.created() > 0 || result.reused() > 0 || result.skipped() > 0) {
                log.info(
                        "Direct local evidence reconciled: candidates={}, created={}, reused={}, finalized={}, rejected={}, skipped={}, batchIds={}, settlementIds={}",
                        result.candidates(),
                        result.created(),
                        result.reused(),
                        result.finalized(),
                        result.rejected(),
                        result.skipped(),
                        result.batchIds(),
                        result.settlementIds()
                );
            }
        } catch (RuntimeException exception) {
            log.warn("Direct local evidence reconciliation failed: {}", exception.getMessage(), exception);
        }

        try {
            autoConfirmStalePendingReceiverHistory();
        } catch (RuntimeException exception) {
            log.warn("Auto-confirm stale pending receiver history failed: {}", exception.getMessage(), exception);
        }
    }

    private void autoConfirmStalePendingReceiverHistory() {
        long delayMs = properties.worker().receiverHistoryAutoConfirmDelayMs();
        OffsetDateTime cutoff = OffsetDateTime.now().minus(Duration.ofMillis(delayMs));
        List<OfflineSaga> staleSagas = offlineSagaService.findReceiverHistoryPendingStale(
                cutoff,
                properties.worker().receiverHistoryPendingScanLimit()
        );
        if (staleSagas == null || staleSagas.isEmpty()) {
            return;
        }
        SettlementApplicationService.AutoConfirmPendingReceiverHistoryResult result =
                settlementApplicationService.autoConfirmStalePendingReceiverHistory(staleSagas);
        if (result.candidates() > 0 || result.confirmed() > 0) {
            log.info(
                    "Auto-confirmed stale pending receiver history: candidates={}, attempted={}, confirmed={}, skipped={}",
                    result.candidates(),
                    result.attempted(),
                    result.confirmed(),
                    result.skipped()
            );
        }
    }
}
