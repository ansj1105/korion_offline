package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationFollowUpWorker {

    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final SettlementBatchEventBus settlementBatchEventBus;
    private final JsonService jsonService;
    private final AppProperties properties;

    public ReconciliationFollowUpWorker(
            ReconciliationCaseRepository reconciliationCaseRepository,
            SettlementBatchEventBus settlementBatchEventBus,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.settlementBatchEventBus = settlementBatchEventBus;
        this.jsonService = jsonService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.poll-delay-ms:5000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }

        List<ReconciliationCase> candidates =
                reconciliationCaseRepository.findOpenRetryable(properties.settlementStreamBatchSize());
        for (ReconciliationCase candidate : candidates) {
            JsonNode detail = jsonService.readTree(candidate.detailJson());
            if (!detail.path("retryable").asBoolean(false)) {
                continue;
            }
            String nextAction = detail.path("nextAction").asText("");
            if (!"RETRY_EXTERNAL_SYNC".equals(nextAction) && !"RETRY_COLLATERAL_SYNC".equals(nextAction)) {
                continue;
            }
            if (!isRetryDue(detail.path("nextRetryAt").asText(null))) {
                continue;
            }
            if ("RETRY_EXTERNAL_SYNC".equals(nextAction)) {
                String eventType = detail.path("eventType").asText("");
                String payloadJson = detail.path("payloadJson").asText("");
                if (eventType.isBlank() || payloadJson.isBlank()) {
                    continue;
                }
                settlementBatchEventBus.publishExternalSyncRequested(
                        eventType,
                        candidate.settlementId(),
                        candidate.batchId(),
                        candidate.proofId(),
                        payloadJson,
                        OffsetDateTime.now().toString()
                );
            } else {
                String operationId = detail.path("operationId").asText("");
                String operationType = detail.path("operationType").asText("");
                String assetCode = detail.path("assetCode").asText("");
                String referenceId = detail.path("referenceId").asText("");
                if (operationId.isBlank() || operationType.isBlank() || referenceId.isBlank()) {
                    continue;
                }
                settlementBatchEventBus.publishCollateralOperationRequested(
                        operationId,
                        operationType,
                        assetCode,
                        referenceId,
                        OffsetDateTime.now().toString()
                );
            }
            reconciliationCaseRepository.updateDetail(candidate.id(), updateDetail(detail));
        }
    }

    private boolean isRetryDue(String nextRetryAt) {
        if (nextRetryAt == null || nextRetryAt.isBlank()) {
            return true;
        }
        try {
            return !OffsetDateTime.now().isBefore(OffsetDateTime.parse(nextRetryAt));
        } catch (DateTimeParseException ignored) {
            return true;
        }
    }

    private String updateDetail(JsonNode detail) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        detail.fields().forEachRemaining(entry -> merged.put(entry.getKey(), entry.getValue()));
        int retryCount = detail.path("retryCount").asInt(0) + 1;
        merged.put("retryCount", retryCount);
        merged.put("lastRetriedAt", OffsetDateTime.now().toString());
        merged.put("nextRetryAt", OffsetDateTime.now().plusMinutes(5).toString());
        return jsonService.write(merged);
    }
}
