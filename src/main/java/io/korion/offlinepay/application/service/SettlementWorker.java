package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SettlementWorker {

    private final SettlementBatchEventBus eventBus;
    private final SettlementApplicationService settlementApplicationService;
    private final AppProperties properties;

    public SettlementWorker(
            SettlementBatchEventBus eventBus,
            SettlementApplicationService settlementApplicationService,
            AppProperties properties
    ) {
        this.eventBus = eventBus;
        this.settlementApplicationService = settlementApplicationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.poll-delay-ms:5000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }

        List<SettlementBatchEventBus.QueuedBatchMessage> batchMessages = new java.util.ArrayList<>();
        batchMessages.addAll(eventBus.pollRequestedBatches(properties.settlementStreamBatchSize()));
        batchMessages.addAll(eventBus.reclaimStaleRequestedBatches(
                properties.settlementStreamBatchSize(),
                properties.worker().claimIdleMs()
        ));
        for (SettlementBatchEventBus.QueuedBatchMessage batchMessage : batchMessages) {
            try {
                settlementApplicationService.markBatchValidating(batchMessage.batchId());
                settlementApplicationService.finalizeBatch(batchMessage.batchId());
                eventBus.acknowledgeRequested(batchMessage.messageId());
            } catch (RuntimeException exception) {
                SettlementApplicationService.BatchFailureOutcome failureOutcome =
                        settlementApplicationService.recordBatchProcessingFailure(
                                batchMessage.batchId(),
                                exception.getMessage() == null ? "unknown worker failure" : exception.getMessage(),
                                properties.worker().maxAttempts()
                        );
                if (failureOutcome.deadLettered()) {
                    eventBus.publishDeadLetter(
                            failureOutcome.batchId(),
                            failureOutcome.attemptCount(),
                            exception.getMessage() == null ? "unknown worker failure" : exception.getMessage(),
                            java.time.OffsetDateTime.now().toString()
                    );
                    eventBus.acknowledgeRequested(batchMessage.messageId());
                }
            }
        }
    }
}
