package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrphanReceivedUnsettledCleanupWorker {

    private static final Logger log = LoggerFactory.getLogger(OrphanReceivedUnsettledCleanupWorker.class);
    private static final DateTimeFormatter HOURLY_REFERENCE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final OfflinePaymentProofRepository proofRepository;
    private final AppProperties properties;

    public OrphanReceivedUnsettledCleanupWorker(
            OfflinePaymentProofRepository proofRepository,
            AppProperties properties
    ) {
        this.proofRepository = proofRepository;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${offline-pay.worker.orphan-received-unsettled-cleanup-cron:0 20 4 * * *}",
            zone = "${offline-pay.worker.orphan-received-unsettled-cleanup-zone:UTC}"
    )
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now()
                .minus(Duration.ofMillis(properties.worker().orphanReceivedUnsettledAgeMs()));
        List<OfflinePaymentProof> candidates = proofRepository.findOrphanReceivedUnsettledCandidates(
                cutoff,
                properties.worker().orphanReceivedUnsettledCleanupLimit()
        );
        if (candidates.isEmpty()) {
            return;
        }

        List<String> proofIds = candidates.stream()
                .map(OfflinePaymentProof::id)
                .toList();
        String referenceId = "orphan-cleanup:" + OffsetDateTime.now().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        int settled = proofRepository.markReceivedCollateralSettled(proofIds, null, referenceId);
        log.warn(
                "Orphan received unsettled cleanup closed candidates={}, settled={}, cutoff={}, referenceId={}, proofIds={}",
                candidates.size(),
                settled,
                cutoff,
                referenceId,
                proofIds
        );
    }

    @Scheduled(
            cron = "${offline-pay.worker.settled-received-unsettled-cleanup-cron:0 0 * * * *}",
            zone = "${offline-pay.worker.settled-received-unsettled-cleanup-zone:UTC}"
    )
    public void cleanupFinalizedReceivedUnsettled() {
        if (!properties.worker().enabled()) {
            return;
        }

        List<OfflinePaymentProof> candidates = proofRepository.findFinalizedReceivedUnsettledCandidates(
                properties.worker().settledReceivedUnsettledCleanupLimit()
        );
        if (candidates.isEmpty()) {
            return;
        }

        List<String> proofIds = candidates.stream()
                .map(OfflinePaymentProof::id)
                .toList();
        String referenceId = "finalized-received-cleanup:" + OffsetDateTime.now().format(HOURLY_REFERENCE_FORMAT);
        int settled = proofRepository.markReceivedUnsettledAsSettledForFinalizedSettlements(proofIds, referenceId);
        log.warn(
                "Finalized received unsettled cleanup closed candidates={}, settled={}, referenceId={}, proofIds={}",
                candidates.size(),
                settled,
                referenceId,
                proofIds
        );
    }
}
