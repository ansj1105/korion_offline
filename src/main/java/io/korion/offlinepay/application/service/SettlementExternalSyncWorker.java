package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.policy.OfflineFailureClass;
import io.korion.offlinepay.domain.policy.OfflineFailurePolicy;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.OfflineWorkflowEventType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SettlementExternalSyncWorker {

    private final SettlementBatchEventBus eventBus;
    private final CoinManageSettlementPort coinManageSettlementPort;
    private final FoxCoinHistoryPort foxCoinHistoryPort;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final OfflineSagaService offlineSagaService;
    private final JsonService jsonService;
    private final AppProperties properties;

    public SettlementExternalSyncWorker(
            SettlementBatchEventBus eventBus,
            CoinManageSettlementPort coinManageSettlementPort,
            FoxCoinHistoryPort foxCoinHistoryPort,
            ReconciliationCaseRepository reconciliationCaseRepository,
            OfflineSagaService offlineSagaService,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.eventBus = eventBus;
        this.coinManageSettlementPort = coinManageSettlementPort;
        this.foxCoinHistoryPort = foxCoinHistoryPort;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.offlineSagaService = offlineSagaService;
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
        if (OfflineWorkflowEventType.LEDGER_SYNC_REQUESTED.name().equals(message.eventType())) {
            CoinManageSettlementPort.SettlementLedgerCommand command = toLedgerCommand(payload.path("ledgerCommand"));
            CoinManageSettlementPort.SettlementLedgerResult ledgerResult = coinManageSettlementPort.finalizeSettlement(command);
            offlineSagaService.markPartiallyApplied(
                    OfflineSagaType.SETTLEMENT,
                    message.settlementId(),
                    "LEDGER_SYNCED",
                    Map.of(
                            "settlementId", message.settlementId(),
                            "batchId", message.batchId(),
                            "proofId", message.proofId(),
                            "eventType", message.eventType(),
                            "ledgerResult", Map.ofEntries(
                                    Map.entry("settlementId", ledgerResult.settlementId()),
                                    Map.entry("ledgerOutcome", ledgerResult.ledgerOutcome()),
                                    Map.entry("releaseAction", ledgerResult.releaseAction()),
                                    Map.entry("duplicated", ledgerResult.duplicated()),
                                    Map.entry("accountingSide", ledgerResult.accountingSide()),
                                    Map.entry("receiverSettlementMode", ledgerResult.receiverSettlementMode()),
                                    Map.entry("settlementModel", ledgerResult.settlementModel()),
                                    Map.entry("reconciliationTrackingOwner", ledgerResult.reconciliationTrackingOwner()),
                                    Map.entry("postAvailableBalance", ledgerResult.postAvailableBalance().toPlainString()),
                                    Map.entry("postLockedBalance", ledgerResult.postLockedBalance().toPlainString()),
                                    Map.entry("postOfflinePayPendingBalance", ledgerResult.postOfflinePayPendingBalance().toPlainString())
                            )
                    )
            );
            resolveReconciliationCases(
                    message.settlementId(),
                    List.of("LEDGER_SYNC_FAILED", "LEDGER_CIRCUIT_OPEN"),
                    "LEDGER_SYNC_RESOLVED",
                    message.eventType()
            );
            Map<String, Object> historyPayload = new java.util.LinkedHashMap<>();
            historyPayload.put("settlementId", message.settlementId());
            historyPayload.put("batchId", message.batchId());
            historyPayload.put("proofId", message.proofId());
            historyPayload.put("ledgerCommand", payload.path("ledgerCommand"));
            historyPayload.put("historyCommand", payload.path("historyCommand"));
            historyPayload.put("requestedAt", OffsetDateTime.now().toString());
            if (!payload.path("receiverHistoryCommand").isMissingNode()) {
                historyPayload.put("receiverHistoryCommand", payload.path("receiverHistoryCommand"));
            }
            eventBus.publishExternalSyncRequested(
                    OfflineWorkflowEventType.HISTORY_SYNC_REQUESTED.name(),
                    message.settlementId(),
                    message.batchId(),
                    message.proofId(),
                    jsonService.write(historyPayload),
                    OffsetDateTime.now().toString()
            );
            return;
        }

        if (OfflineWorkflowEventType.HISTORY_SYNC_REQUESTED.name().equals(message.eventType())) {
            FoxCoinHistoryPort.SettlementHistoryCommand command = toHistoryCommand(payload.path("historyCommand"));
            foxCoinHistoryPort.recordSettlementHistory(command);
            // receiver history: chain into separate step to keep sender history idempotency separate
            if (!payload.path("receiverHistoryCommand").isMissingNode()) {
                eventBus.publishExternalSyncRequested(
                        OfflineWorkflowEventType.RECEIVER_HISTORY_SYNC_REQUESTED.name(),
                        message.settlementId(),
                        message.batchId(),
                        message.proofId(),
                        jsonService.write(Map.of(
                                "settlementId", message.settlementId(),
                                "batchId", message.batchId(),
                                "proofId", message.proofId(),
                                "receiverHistoryCommand", payload.path("receiverHistoryCommand"),
                                "ledgerCommand", payload.path("ledgerCommand"),
                                "historyCompensationCommand", toHistoryCompensationPayload(payload.path("historyCommand")),
                                "requestedAt", OffsetDateTime.now().toString()
                        )),
                        OffsetDateTime.now().toString()
                );
                offlineSagaService.markPartiallyApplied(
                        OfflineSagaType.SETTLEMENT,
                        message.settlementId(),
                        "HISTORY_SYNCED",
                        Map.of(
                                "settlementId", message.settlementId(),
                                "batchId", message.batchId(),
                                "proofId", message.proofId(),
                                "eventType", message.eventType(),
                                "senderHistorySynced", true,
                                "receiverHistoryPending", true
                        )
                );
            } else {
                offlineSagaService.markCompleted(
                        OfflineSagaType.SETTLEMENT,
                        message.settlementId(),
                        "HISTORY_SYNCED",
                        Map.of(
                                "settlementId", message.settlementId(),
                                "batchId", message.batchId(),
                                "proofId", message.proofId(),
                                "eventType", message.eventType()
                        )
                );
            }
            resolveReconciliationCases(
                    message.settlementId(),
                    List.of("HISTORY_SYNC_FAILED", "HISTORY_CIRCUIT_OPEN"),
                    "HISTORY_SYNC_RESOLVED",
                    message.eventType()
            );
            return;
        }

        if (OfflineWorkflowEventType.RECEIVER_HISTORY_SYNC_REQUESTED.name().equals(message.eventType())) {
            FoxCoinHistoryPort.SettlementHistoryCommand receiverCommand = toHistoryCommand(payload.path("receiverHistoryCommand"));
            foxCoinHistoryPort.recordSettlementHistory(receiverCommand);
            offlineSagaService.markCompleted(
                    OfflineSagaType.SETTLEMENT,
                    message.settlementId(),
                    "RECEIVER_HISTORY_SYNCED",
                    Map.of(
                            "settlementId", message.settlementId(),
                            "batchId", message.batchId(),
                            "proofId", message.proofId(),
                            "receiverHistorySynced", true,
                            "eventType", message.eventType()
                    )
            );
            return;
        }

        if (OfflineWorkflowEventType.LEDGER_COMPENSATION_REQUESTED.name().equals(message.eventType())) {
            CoinManageSettlementPort.SettlementCompensationCommand command = toCompensationCommand(payload.path("compensationCommand"));
            offlineSagaService.markCompensating(
                    OfflineSagaType.SETTLEMENT,
                    message.settlementId(),
                    "COMPENSATING",
                    Map.of(
                            "settlementId", message.settlementId(),
                            "batchId", message.batchId(),
                            "proofId", message.proofId(),
                            "eventType", message.eventType()
                    )
            );
            CoinManageSettlementPort.SettlementLedgerResult ledgerResult = coinManageSettlementPort.compensateSettlement(command);
            if (!payload.path("historyCompensationCommand").isMissingNode()) {
                FoxCoinHistoryPort.SettlementHistoryCommand historyCompensationCommand =
                        toHistoryCommand(payload.path("historyCompensationCommand"));
                foxCoinHistoryPort.recordSettlementHistory(historyCompensationCommand);
            }
            offlineSagaService.markCompensated(
                    OfflineSagaType.SETTLEMENT,
                    message.settlementId(),
                    "COMPENSATED",
                    Map.of(
                            "settlementId", message.settlementId(),
                            "batchId", message.batchId(),
                            "proofId", message.proofId(),
                            "eventType", message.eventType(),
                            "ledgerResult", Map.ofEntries(
                                    Map.entry("settlementId", ledgerResult.settlementId()),
                                    Map.entry("ledgerOutcome", ledgerResult.ledgerOutcome()),
                                    Map.entry("releaseAction", ledgerResult.releaseAction()),
                                    Map.entry("duplicated", ledgerResult.duplicated()),
                                    Map.entry("accountingSide", ledgerResult.accountingSide()),
                                    Map.entry("receiverSettlementMode", ledgerResult.receiverSettlementMode()),
                                    Map.entry("settlementModel", ledgerResult.settlementModel()),
                                    Map.entry("reconciliationTrackingOwner", ledgerResult.reconciliationTrackingOwner()),
                                    Map.entry("postAvailableBalance", ledgerResult.postAvailableBalance().toPlainString()),
                                    Map.entry("postLockedBalance", ledgerResult.postLockedBalance().toPlainString()),
                                    Map.entry("postOfflinePayPendingBalance", ledgerResult.postOfflinePayPendingBalance().toPlainString())
                            )
                    )
            );
            resolveReconciliationCases(
                    message.settlementId(),
                    List.of("HISTORY_SYNC_FAILED", "HISTORY_CIRCUIT_OPEN"),
                    "LEDGER_COMPENSATED",
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
        JsonNode payload = jsonService.readTree(message.payloadJson());
        String errorMessage = exception.getMessage() == null ? "unknown external sync failure" : exception.getMessage();
        OfflineFailureClass failureClass = OfflineFailurePolicy.classify(reasonCode, errorMessage);
        boolean retryable = OfflineFailurePolicy.isRetryable(failureClass);
        if (OfflineWorkflowEventType.HISTORY_SYNC_REQUESTED.name().equals(message.eventType())
                || OfflineWorkflowEventType.RECEIVER_HISTORY_SYNC_REQUESTED.name().equals(message.eventType())) {
            JsonNode compensationCommandNode = payload.path("compensationCommand");
            Object compensationCommand = compensationCommandNode;
            if (compensationCommandNode.isMissingNode() || compensationCommandNode.isNull() || !compensationCommandNode.isObject()) {
                compensationCommand = toCompensationPayload(payload.path("ledgerCommand"), reasonCode);
            }
            offlineSagaService.markCompensationRequired(
                    OfflineSagaType.SETTLEMENT,
                    message.settlementId(),
                    "COMPENSATION_REQUIRED",
                    reasonCode,
                    Map.of(
                            "settlementId", message.settlementId(),
                            "batchId", message.batchId(),
                            "proofId", message.proofId(),
                            "errorMessage", errorMessage,
                            "eventType", message.eventType()
                    )
            );
            eventBus.publishExternalSyncRequested(
                    OfflineWorkflowEventType.LEDGER_COMPENSATION_REQUESTED.name(),
                    message.settlementId(),
                    message.batchId(),
                    message.proofId(),
                    jsonService.write(buildCompensationEventPayload(
                            message,
                            compensationCommand,
                            payload.path("historyCompensationCommand")
                    )),
                    OffsetDateTime.now().toString()
            );
        } else if (retryable) {
            offlineSagaService.markDeadLettered(
                    OfflineSagaType.SETTLEMENT,
                    message.settlementId(),
                    "DEAD_LETTERED",
                    reasonCode,
                    Map.of(
                            "settlementId", message.settlementId(),
                            "batchId", message.batchId(),
                            "proofId", message.proofId(),
                            "errorMessage", errorMessage,
                            "eventType", message.eventType()
                    )
            );
        } else {
            offlineSagaService.markFailed(
                    OfflineSagaType.SETTLEMENT,
                    message.settlementId(),
                    "FAILED",
                    reasonCode,
                    Map.of(
                            "settlementId", message.settlementId(),
                            "batchId", message.batchId(),
                            "proofId", message.proofId(),
                            "errorMessage", errorMessage,
                            "eventType", message.eventType()
                    )
            );
        }
        reconciliationCaseRepository.save(
                message.settlementId(),
                message.batchId(),
                message.proofId(),
                extractVoucherId(payload),
                caseType,
                io.korion.offlinepay.domain.status.ReconciliationCaseStatus.OPEN,
                reasonCode,
                jsonService.write(Map.ofEntries(
                        Map.entry("settlementId", message.settlementId()),
                        Map.entry("batchId", message.batchId()),
                        Map.entry("proofId", message.proofId()),
                        Map.entry("failureClass", failureClass.name()),
                        Map.entry("retryable", retryable),
                        Map.entry("adminAction", OfflineFailurePolicy.adminAction(failureClass)),
                        Map.entry("nextAction", "RETRY_EXTERNAL_SYNC"),
                        Map.entry("syncTarget", resolveSyncTarget(message.eventType())),
                        Map.entry("eventType", message.eventType()),
                        Map.entry("payloadJson", message.payloadJson()),
                        Map.entry("retryCount", message.attempts()),
                        Map.entry(
                                "nextRetryAt",
                                retryable
                                        ? OffsetDateTime.now()
                                                .plus(OfflineFailurePolicy.nextRetryDelay(failureClass, message.attempts()))
                                                .toString()
                                        : ""
                        ),
                        Map.entry("errorMessage", errorMessage)
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
        if (OfflineWorkflowEventType.LEDGER_SYNC_REQUESTED.name().equals(eventType)) {
            return circuitOpen ? "LEDGER_CIRCUIT_OPEN" : "LEDGER_SYNC_FAILED";
        }
        if (OfflineWorkflowEventType.HISTORY_SYNC_REQUESTED.name().equals(eventType)
                || OfflineWorkflowEventType.RECEIVER_HISTORY_SYNC_REQUESTED.name().equals(eventType)) {
            return circuitOpen ? "HISTORY_CIRCUIT_OPEN" : "HISTORY_SYNC_FAILED";
        }
        if (OfflineWorkflowEventType.LEDGER_COMPENSATION_REQUESTED.name().equals(eventType)) {
            return "LEDGER_COMPENSATION_FAILED";
        }
        return "FAILED_SETTLEMENT";
    }

    private String resolveFailureReasonCode(String eventType, RuntimeException exception) {
        boolean circuitOpen = isCircuitOpen(exception);
        if (OfflineWorkflowEventType.LEDGER_SYNC_REQUESTED.name().equals(eventType)) {
            return circuitOpen ? OfflinePayReasonCode.LEDGER_CIRCUIT_OPEN : OfflinePayReasonCode.LEDGER_SYNC_FAIL;
        }
        if (OfflineWorkflowEventType.HISTORY_SYNC_REQUESTED.name().equals(eventType)
                || OfflineWorkflowEventType.RECEIVER_HISTORY_SYNC_REQUESTED.name().equals(eventType)) {
            return circuitOpen ? OfflinePayReasonCode.HISTORY_CIRCUIT_OPEN : OfflinePayReasonCode.HISTORY_SYNC_FAIL;
        }
        if (OfflineWorkflowEventType.LEDGER_COMPENSATION_REQUESTED.name().equals(eventType)) {
            return OfflinePayReasonCode.SETTLEMENT_FAIL;
        }
        return OfflinePayReasonCode.SETTLEMENT_FAIL;
    }

    private String resolveSyncTarget(String eventType) {
        if (OfflineWorkflowEventType.LEDGER_SYNC_REQUESTED.name().equals(eventType)
                || OfflineWorkflowEventType.LEDGER_COMPENSATION_REQUESTED.name().equals(eventType)) {
            return "COIN_MANAGE_LEDGER";
        }
        return "FOXYA_HISTORY";
    }

    private boolean isCircuitOpen(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("circuit is open");
    }

    private Map<String, Object> buildCompensationEventPayload(
            SettlementBatchEventBus.QueuedExternalSyncMessage message,
            Object compensationCommand,
            JsonNode historyCompensationCommand
    ) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("settlementId", message.settlementId());
        payload.put("batchId", message.batchId());
        payload.put("proofId", message.proofId());
        payload.put("compensationCommand", compensationCommand);
        if (!historyCompensationCommand.isMissingNode() && !historyCompensationCommand.isNull()) {
            payload.put("historyCompensationCommand", historyCompensationCommand);
        }
        payload.put("requestedAt", OffsetDateTime.now().toString());
        return payload;
    }

    private Map<String, Object> toCompensationPayload(JsonNode ledgerCommand, String compensationReason) {
        return Map.ofEntries(
                Map.entry("settlementId", requireText(ledgerCommand, "settlementId")),
                Map.entry("batchId", requireText(ledgerCommand, "batchId")),
                Map.entry("collateralId", requireText(ledgerCommand, "collateralId")),
                Map.entry("proofId", requireText(ledgerCommand, "proofId")),
                Map.entry("userId", ledgerCommand.path("userId").asLong()),
                Map.entry("deviceId", requireText(ledgerCommand, "deviceId")),
                Map.entry("assetCode", requireText(ledgerCommand, "assetCode")),
                Map.entry("amount", ledgerCommand.path("amount").decimalValue()),
                Map.entry("releaseAction", requireText(ledgerCommand, "releaseAction")),
                Map.entry("proofFingerprint", requireText(ledgerCommand, "proofFingerprint")),
                Map.entry("compensationReason", compensationReason)
        );
    }

    private Map<String, Object> toHistoryCompensationPayload(JsonNode historyCommand) {
        String settlementId = requireText(historyCommand, "settlementId");
        return Map.ofEntries(
                Map.entry("settlementId", settlementId),
                Map.entry("transferRef", settlementId + ":C"),
                Map.entry("batchId", requireText(historyCommand, "batchId")),
                Map.entry("collateralId", requireText(historyCommand, "collateralId")),
                Map.entry("proofId", requireText(historyCommand, "proofId")),
                Map.entry("userId", historyCommand.path("userId").asLong()),
                Map.entry("deviceId", requireText(historyCommand, "deviceId")),
                Map.entry("assetCode", requireText(historyCommand, "assetCode")),
                Map.entry("amount", historyCommand.path("amount").decimalValue()),
                Map.entry("settlementStatus", "COMPENSATED"),
                Map.entry("historyType", "OFFLINE_PAY_COMPENSATION")
        );
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

    private CoinManageSettlementPort.SettlementCompensationCommand toCompensationCommand(JsonNode node) {
        return new CoinManageSettlementPort.SettlementCompensationCommand(
                requireText(node, "settlementId"),
                requireText(node, "batchId"),
                requireText(node, "collateralId"),
                requireText(node, "proofId"),
                node.path("userId").asLong(),
                requireText(node, "deviceId"),
                requireText(node, "assetCode"),
                node.path("amount").decimalValue(),
                requireText(node, "releaseAction"),
                requireText(node, "proofFingerprint"),
                requireText(node, "compensationReason")
        );
    }

    private FoxCoinHistoryPort.SettlementHistoryCommand toHistoryCommand(JsonNode node) {
        String settlementId = requireText(node, "settlementId");
        String transferRef = node.hasNonNull("transferRef") ? node.path("transferRef").asText() : settlementId;
        return new FoxCoinHistoryPort.SettlementHistoryCommand(
                settlementId,
                transferRef,
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
