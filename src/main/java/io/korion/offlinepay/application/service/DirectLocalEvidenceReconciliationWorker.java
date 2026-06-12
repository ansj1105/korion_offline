package io.korion.offlinepay.application.service;

import io.korion.offlinepay.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DirectLocalEvidenceReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(DirectLocalEvidenceReconciliationWorker.class);

    private final SettlementApplicationService settlementApplicationService;
    private final AppProperties properties;

    public DirectLocalEvidenceReconciliationWorker(
            SettlementApplicationService settlementApplicationService,
            AppProperties properties
    ) {
        this.settlementApplicationService = settlementApplicationService;
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
            if (result.candidates() > 0 || result.created() > 0 || result.skipped() > 0) {
                log.info(
                        "Direct local evidence reconciled: candidates={}, created={}, finalized={}, rejected={}, skipped={}, batchIds={}, settlementIds={}",
                        result.candidates(),
                        result.created(),
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
    }
}
