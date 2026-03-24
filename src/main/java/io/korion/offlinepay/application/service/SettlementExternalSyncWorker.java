package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SettlementExternalSyncWorker {

    private static final String EVENT_LEDGER_SYNC_REQUESTED = "LEDGER_SYNC_REQUESTED";
    private static final String EVENT_HISTORY_SYNC_REQUESTED = "HISTORY_SYNC_REQUESTED";

    private final SettlementBatchEventBus eventBus;
    private final CoinManageSettlementPort coinManageSettlementPort;
    private final FoxCoinHistoryPort foxCoinHistoryPort;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final JsonService jsonService;
    private final AppProperties properties;

    public SettlementExternalSyncWorker(
            SettlementBatchEventBus eventBus,
            CoinManageSettlementPort coinManageSettlementPort,
            FoxCoinHistoryPort foxCoinHistoryPort,
            ReconciliationCaseRepository reconciliationCaseRepository,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.eventBus = eventBus;
        this.coinManageSettlementPort = coinManageSettlementPort;
        this.foxCoinHistoryPort = foxCoinHistoryPort;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.jsonService = jsonService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.poll-delay-ms:5000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }

        List<SettlementBatchEventBus.QueuedExternalSyncMessage> messages = new java.util.ArrayList<>();
        messages.addAll(eventBus.pollExternalSyncRequested(properties.settlementStreamBatchSize()));
        messages.addAll(eventBus.reclaimStaleExternalSyncRequested(
                properties.settlementStreamBatchSize(),
                properties.worker().claimIdleMs()
        ));

        for (SettlementBatchEventBus.QueuedExternalSyncMessage message : messages) {
            try {
                process(message);
                eventBus.acknowledgeExternalSync(message.messageId());
            } catch (RuntimeException exception) {
                if (message.attempts() >= properties.worker().maxAttempts()) {
                    eventBus.publishExternalSyncDeadLetter(
                            message.eventType(),
                            message.settlementId(),
                            message.batchId(),
                            message.proofId(),
                            message.attempts(),
                            resolveFailureReasonCode(message.eventType(), exception),
                            exception.getMessage() == null ? "unknown external sync failure" : exception.getMessage(),
                            OffsetDateTime.now().toString()
                    );
                    ensureReconciliationCase(
                            message,
                            resolveFailureCaseType(message.eventType(), exception),
                            resolveFailureReasonCode(message.eventType(), exception),
                            exception
                    );
                    eventBus.acknowledgeExternalSync(message.messageId());
                }
            }
        }
    }

    private void process(SettlementBatchEventBus.QueuedExternalSyncMessage message) {
        JsonNode payload = jsonService.readTree(message.payloadJson());
        if (EVENT_LEDGER_SYNC_REQUESTED.equals(message.eventType())) {
            CoinManageSettlementPort.SettlementLedgerCommand command = toLedgerCommand(payload.path("ledgerCommand"));
            coinManageSettlementPort.finalizeSettlement(command);
            resolveReconciliationCases(
                    message.settlementId(),
                    List.of("LEDGER_SYNC_FAILED", "LEDGER_CIRCUIT_OPEN"),
                    "LEDGER_SYNC_RESOLVED",
                    message.eventType()
            );
            eventBus.publishExternalSyncRequested(
                    EVENT_HISTORY_SYNC_REQUESTED,
                    message.settlementId(),
                    message.batchId(),
                    message.proofId(),
                    jsonService.write(Map.of(
                            "settlementId", message.settlementId(),
                            "batchId", message.batchId(),
                            "proofId", message.proofId(),
                            "historyCommand", payload.path("historyCommand"),
                            "requestedAt", OffsetDateTime.now().toString()
                    )),
                    OffsetDateTime.now().toString()
            );
            return;
        }

        if (EVENT_HISTORY_SYNC_REQUESTED.equals(message.eventType())) {
            FoxCoinHistoryPort.SettlementHistoryCommand command = toHistoryCommand(payload.path("historyCommand"));
            foxCoinHistoryPort.recordSettlementHistory(command);
            resolveReconciliationCases(
                    message.settlementId(),
                    List.of("HISTORY_SYNC_FAILED", "HISTORY_CIRCUIT_OPEN"),
                    "HISTORY_SYNC_RESOLVED",
                    message.eventType()
            );
            return;
        }

        throw new IllegalArgumentException("unsupported external sync message: " + message.eventType());
    }

    private void ensureReconciliationCase(
            SettlementBatchEventBus.QueuedExternalSyncMessage message,
            String caseType,
            String reasonCode,
            RuntimeException exception
    ) {
        if (reconciliationCaseRepository.findOpenBySettlementIdAndCaseType(message.settlementId(), caseType).isPresent()) {
            return;
        }
        reconciliationCaseRepository.save(
                message.settlementId(),
                message.batchId(),
                message.proofId(),
                extractVoucherId(jsonService.readTree(message.payloadJson())),
                caseType,
                io.korion.offlinepay.domain.status.ReconciliationCaseStatus.OPEN,
                reasonCode,
                jsonService.write(Map.ofEntries(
                        Map.entry("settlementId", message.settlementId()),
                        Map.entry("batchId", message.batchId()),
                        Map.entry("proofId", message.proofId()),
                        Map.entry("retryable", true),
                        Map.entry("nextAction", "RETRY_EXTERNAL_SYNC"),
                        Map.entry("syncTarget", resolveSyncTarget(message.eventType())),
                        Map.entry("eventType", message.eventType()),
                        Map.entry("payloadJson", message.payloadJson()),
                        Map.entry("retryCount", message.attempts()),
                        Map.entry("nextRetryAt", OffsetDateTime.now().plusMinutes(5).toString()),
                        Map.entry(
                                "errorMessage",
                                exception.getMessage() == null ? "unknown external sync failure" : exception.getMessage()
                        )
                ))
        );
    }

    private void resolveReconciliationCases(
            String settlementId,
            List<String> caseTypes,
            String resolutionType,
            String eventType
    ) {
        for (String caseType : caseTypes) {
            reconciliationCaseRepository.findOpenBySettlementIdAndCaseType(settlementId, caseType)
                    .ifPresent(existing -> reconciliationCaseRepository.resolve(
                            existing.id(),
                            mergeResolutionDetail(existing, resolutionType, eventType)
                    ));
        }
    }

    private String mergeResolutionDetail(ReconciliationCase existing, String resolutionType, String eventType) {
        JsonNode detail = jsonService.readTree(existing.detailJson());
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
        detail.fields().forEachRemaining(entry -> merged.put(entry.getKey(), entry.getValue()));
        merged.put("resolvedAt", OffsetDateTime.now().toString());
        merged.put("resolutionType", resolutionType);
        merged.put("resolvedByEventType", eventType);
        return jsonService.write(merged);
    }

    private String extractVoucherId(JsonNode payload) {
        JsonNode historyCommand = payload.path("historyCommand");
        JsonNode ledgerCommand = payload.path("ledgerCommand");
        if (historyCommand.hasNonNull("proofId")) {
            return historyCommand.path("proofId").asText();
        }
        if (ledgerCommand.hasNonNull("proofId")) {
            return ledgerCommand.path("proofId").asText();
        }
        return null;
    }

    private String resolveFailureCaseType(String eventType, RuntimeException exception) {
        boolean circuitOpen = isCircuitOpen(exception);
        if (EVENT_LEDGER_SYNC_REQUESTED.equals(eventType)) {
            return circuitOpen ? "LEDGER_CIRCUIT_OPEN" : "LEDGER_SYNC_FAILED";
        }
        if (EVENT_HISTORY_SYNC_REQUESTED.equals(eventType)) {
            return circuitOpen ? "HISTORY_CIRCUIT_OPEN" : "HISTORY_SYNC_FAILED";
        }
        return "FAILED_SETTLEMENT";
    }

    private String resolveFailureReasonCode(String eventType, RuntimeException exception) {
        boolean circuitOpen = isCircuitOpen(exception);
        if (EVENT_LEDGER_SYNC_REQUESTED.equals(eventType)) {
            return circuitOpen ? OfflinePayReasonCode.LEDGER_CIRCUIT_OPEN : OfflinePayReasonCode.LEDGER_SYNC_FAIL;
        }
        if (EVENT_HISTORY_SYNC_REQUESTED.equals(eventType)) {
            return circuitOpen ? OfflinePayReasonCode.HISTORY_CIRCUIT_OPEN : OfflinePayReasonCode.HISTORY_SYNC_FAIL;
        }
        return OfflinePayReasonCode.SETTLEMENT_FAIL;
    }

    private String resolveSyncTarget(String eventType) {
        return EVENT_LEDGER_SYNC_REQUESTED.equals(eventType) ? "COIN_MANAGE_LEDGER" : "FOXYA_HISTORY";
    }

    private boolean isCircuitOpen(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("circuit is open");
    }

    private CoinManageSettlementPort.SettlementLedgerCommand toLedgerCommand(JsonNode node) {
        return new CoinManageSettlementPort.SettlementLedgerCommand(
                requireText(node, "settlementId"),
                requireText(node, "batchId"),
                requireText(node, "collateralId"),
                requireText(node, "proofId"),
                node.path("userId").asLong(),
                requireText(node, "deviceId"),
                requireText(node, "assetCode"),
                node.path("amount").decimalValue(),
                requireText(node, "settlementStatus"),
                requireText(node, "releaseAction"),
                node.path("conflictDetected").asBoolean(),
                requireText(node, "proofFingerprint"),
                requireText(node, "newStateHash"),
                requireText(node, "previousHash"),
                node.path("monotonicCounter").asLong(),
                requireText(node, "nonce"),
                requireText(node, "signature")
        );
    }

    private FoxCoinHistoryPort.SettlementHistoryCommand toHistoryCommand(JsonNode node) {
        return new FoxCoinHistoryPort.SettlementHistoryCommand(
                requireText(node, "settlementId"),
                requireText(node, "batchId"),
                requireText(node, "collateralId"),
                requireText(node, "proofId"),
                node.path("userId").asLong(),
                requireText(node, "deviceId"),
                requireText(node, "assetCode"),
                node.path("amount").decimalValue(),
                requireText(node, "settlementStatus"),
                requireText(node, "historyType")
        );
    }

    private String requireText(JsonNode node, String field) {
        if (!node.hasNonNull(field) || node.path(field).asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return node.path(field).asText();
    }
}
