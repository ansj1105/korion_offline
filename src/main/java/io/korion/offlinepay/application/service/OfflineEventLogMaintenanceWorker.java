package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.config.AppProperties;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OfflineEventLogMaintenanceWorker {

    private static final Logger log = LoggerFactory.getLogger(OfflineEventLogMaintenanceWorker.class);
    private static final Duration REQUEST_PENDING_TTL = Duration.ofMinutes(5);
    private static final String REQUEST_TIMEOUT_REASON = "REQUEST_TIMEOUT";

    private final OfflineEventLogRepository offlineEventLogRepository;
    private final AppProperties properties;

    public OfflineEventLogMaintenanceWorker(
            OfflineEventLogRepository offlineEventLogRepository,
            AppProperties properties
    ) {
        this.offlineEventLogRepository = offlineEventLogRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.poll-delay-ms:5000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }

        int resolved = offlineEventLogRepository.closePendingResolvedByTerminalEvents();
        int expired = offlineEventLogRepository.expirePendingOlderThan(
                OffsetDateTime.now().minus(REQUEST_PENDING_TTL),
                REQUEST_TIMEOUT_REASON
        );
        if (resolved > 0 || expired > 0) {
            log.info("Offline event pending lifecycle reconciled: resolved={}, expired={}", resolved, expired);
        }
    }
}
