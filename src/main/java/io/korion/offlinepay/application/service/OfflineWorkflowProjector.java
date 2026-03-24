package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.port.OfflineWorkflowStateRepository;
import io.korion.offlinepay.application.port.SettlementOutboxEventRepository;
import io.korion.offlinepay.domain.model.SettlementOutboxEvent;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OfflineWorkflowProjector {

    private final SettlementOutboxEventRepository settlementOutboxEventRepository;
    private final OfflineWorkflowStateRepository offlineWorkflowStateRepository;
    private final JsonService jsonService;

    public OfflineWorkflowProjector(
            SettlementOutboxEventRepository settlementOutboxEventRepository,
            OfflineWorkflowStateRepository offlineWorkflowStateRepository,
            JsonService jsonService
    ) {
        this.settlementOutboxEventRepository = settlementOutboxEventRepository;
        this.offlineWorkflowStateRepository = offlineWorkflowStateRepository;
        this.jsonService = jsonService;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.poll-delay-ms:5000}")
    public void projectRecentOutboxStates() {
        List<SettlementOutboxEvent> events = settlementOutboxEventRepository.findRecent(300, null, null);
        for (SettlementOutboxEvent event : events) {
            project(event);
        }
    }

    private void project(SettlementOutboxEvent event) {
        JsonNode payload = jsonService.readTree(event.payloadJson());
        String workflowStage = payload.path("workflowStage").asText("");
        if (workflowStage.isBlank()) {
            return;
        }

        String workflowType = resolveWorkflowType(event);
        String workflowId = resolveWorkflowId(event, payload);
        if (workflowType == null || workflowId == null || workflowId.isBlank()) {
            return;
        }

        offlineWorkflowStateRepository.upsert(
                workflowType,
                workflowId,
                workflowStage,
                event.eventType(),
                event.id(),
                event.batchId(),
                payload.path("settlementId").asText(null),
                payload.path("operationId").asText(null),
                payload.path("proofId").asText(null),
                payload.path("referenceId").asText(null),
                payload.path("assetCode").asText(null),
                event.reasonCode(),
                event.errorMessage(),
                event.payloadJson()
        );
    }

    private String resolveWorkflowType(SettlementOutboxEvent event) {
        if (event.eventType().startsWith("COLLATERAL_")) {
            return "COLLATERAL";
        }
        if (event.eventType().startsWith("LEDGER_") || event.eventType().startsWith("HISTORY_")
                || event.eventType().startsWith("EXTERNAL_")) {
            return "SETTLEMENT";
        }
        if (event.batchId() != null && !event.batchId().isBlank()) {
            return "BATCH";
        }
        return null;
    }

    private String resolveWorkflowId(SettlementOutboxEvent event, JsonNode payload) {
        if ("COLLATERAL".equals(resolveWorkflowType(event))) {
            return payload.path("operationId").asText("");
        }
        if ("SETTLEMENT".equals(resolveWorkflowType(event))) {
            return payload.path("settlementId").asText("");
        }
        return event.batchId();
    }
}
