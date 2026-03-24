package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CollateralExternalSyncWorker {

    private final SettlementBatchEventBus eventBus;
    private final CollateralOperationRepository collateralOperationRepository;
    private final CollateralRepository collateralRepository;
    private final CoinManageCollateralPort coinManageCollateralPort;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final OfflineSnapshotStreamService offlineSnapshotStreamService;
    private final JsonService jsonService;
    private final TelegramAlertService telegramAlertService;
    private final AppProperties properties;

    public CollateralExternalSyncWorker(
            SettlementBatchEventBus eventBus,
            CollateralOperationRepository collateralOperationRepository,
            CollateralRepository collateralRepository,
            CoinManageCollateralPort coinManageCollateralPort,
            ReconciliationCaseRepository reconciliationCaseRepository,
            OfflineSnapshotStreamService offlineSnapshotStreamService,
            JsonService jsonService,
            TelegramAlertService telegramAlertService,
            AppProperties properties
    ) {
        this.eventBus = eventBus;
        this.collateralOperationRepository = collateralOperationRepository;
        this.collateralRepository = collateralRepository;
        this.coinManageCollateralPort = coinManageCollateralPort;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.offlineSnapshotStreamService = offlineSnapshotStreamService;
        this.jsonService = jsonService;
        this.telegramAlertService = telegramAlertService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.poll-delay-ms:5000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }

        List<SettlementBatchEventBus.QueuedCollateralMessage> messages = new java.util.ArrayList<>();
        messages.addAll(eventBus.pollCollateralOperationRequested(properties.settlementStreamBatchSize()));
        messages.addAll(eventBus.reclaimStaleCollateralOperationRequested(
                properties.settlementStreamBatchSize(),
                properties.worker().claimIdleMs()
        ));

        for (SettlementBatchEventBus.QueuedCollateralMessage message : messages) {
            try {
                process(message);
                eventBus.acknowledgeCollateral(message.messageId());
            } catch (RuntimeException exception) {
                if (message.attempts() >= properties.worker().maxAttempts()) {
                    markDeadLetter(message, exception);
                    eventBus.acknowledgeCollateral(message.messageId());
                }
            }
        }
    }

    private void process(SettlementBatchEventBus.QueuedCollateralMessage message) {
        CollateralOperation operation = collateralOperationRepository.findById(message.operationId())
                .orElseThrow(() -> new IllegalArgumentException("collateral operation not found: " + message.operationId()));
        if (operation.status() == CollateralOperationStatus.COMPLETED) {
            return;
        }

        if (operation.operationType() == CollateralOperationType.TOPUP) {
            processTopup(message, operation);
            return;
        }
        if (operation.operationType() == CollateralOperationType.RELEASE) {
            processRelease(message, operation);
            return;
        }
        throw new IllegalArgumentException("unsupported collateral operation type: " + operation.operationType());
    }

    private void processTopup(SettlementBatchEventBus.QueuedCollateralMessage message, CollateralOperation operation) {
        Map<String, Object> metadata = readMetadata(operation.metadataJson());
        int policyVersion = readInt(metadata.get("policyVersion"), 1);
        String initialStateRoot = readString(metadata.get("initialStateRoot"), "GENESIS");
        CoinManageCollateralPort.LockCollateralResult external = coinManageCollateralPort.lockCollateral(
                operation.userId(),
                operation.deviceId(),
                operation.assetCode(),
                operation.amount(),
                operation.referenceId(),
                policyVersion
        );
        CollateralLock collateral = collateralRepository.save(
                operation.userId(),
                operation.deviceId(),
                operation.assetCode(),
                operation.amount(),
                operation.amount(),
                initialStateRoot,
                policyVersion,
                CollateralStatus.LOCKED,
                external.lockId(),
                OffsetDateTime.now().plusHours(properties.defaultCollateralExpiryHours()),
                jsonService.write(metadata.getOrDefault("metadata", Map.of()))
        );
        collateralOperationRepository.markCompleted(
                operation.referenceId(),
                collateral.id(),
                jsonService.write(Map.of(
                        "externalLockId", external.lockId(),
                        "operationStatus", external.status(),
                        "completedAt", OffsetDateTime.now().toString()
                ))
        );
        eventBus.publishCollateralOperationResult(
                operation.id(),
                operation.operationType().name(),
                "COMPLETED",
                operation.assetCode(),
                operation.referenceId(),
                OffsetDateTime.now().toString(),
                "",
                null
        );
        resolveReconciliation(operation, "COLLATERAL_LOCK_FAILED", "COLLATERAL_RESYNC_RESOLVED");
        offlineSnapshotStreamService.publishCollateralChanged(
                operation.userId(),
                operation.deviceId(),
                operation.assetCode(),
                "TOPUP_COMPLETED"
        );
    }

    private void processRelease(SettlementBatchEventBus.QueuedCollateralMessage message, CollateralOperation operation) {
        if (operation.collateralId() == null || operation.collateralId().isBlank()) {
            throw new IllegalArgumentException("collateralId is required for release operation");
        }
        var collateral = collateralRepository.findById(operation.collateralId())
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + operation.collateralId()));
        coinManageCollateralPort.releaseCollateral(
                operation.userId(),
                operation.deviceId(),
                collateral.id(),
                operation.assetCode(),
                operation.amount(),
                operation.referenceId()
        );
        collateralRepository.deductRemainingAmount(collateral.id(), operation.amount());
        collateralRepository.updateStatus(
                collateral.id(),
                CollateralStatus.RELEASED,
                jsonService.write(Map.of(
                        "referenceId", operation.referenceId(),
                        "releasedAt", OffsetDateTime.now().toString()
                ))
        );
        collateralOperationRepository.markCompleted(
                operation.referenceId(),
                collateral.id(),
                jsonService.write(Map.of(
                        "status", "RELEASED",
                        "completedAt", OffsetDateTime.now().toString()
                ))
        );
        eventBus.publishCollateralOperationResult(
                operation.id(),
                operation.operationType().name(),
                "COMPLETED",
                operation.assetCode(),
                operation.referenceId(),
                OffsetDateTime.now().toString(),
                "",
                null
        );
        resolveReconciliation(operation, "COLLATERAL_RELEASE_FAILED", "COLLATERAL_RESYNC_RESOLVED");
        offlineSnapshotStreamService.publishCollateralChanged(
                operation.userId(),
                operation.deviceId(),
                operation.assetCode(),
                "RELEASE_COMPLETED"
        );
    }

    private void markDeadLetter(SettlementBatchEventBus.QueuedCollateralMessage message, RuntimeException exception) {
        CollateralOperation operation = collateralOperationRepository.findById(message.operationId())
                .orElseThrow(() -> new IllegalArgumentException("collateral operation not found: " + message.operationId()));
        String reasonCode = resolveFailureReasonCode(operation.operationType());
        String errorMessage = exception.getMessage() == null ? "unknown collateral sync failure" : exception.getMessage();
        collateralOperationRepository.markFailed(
                operation.referenceId(),
                errorMessage,
                jsonService.write(Map.of(
                        "failedAt", OffsetDateTime.now().toString(),
                        "attemptCount", message.attempts()
                ))
        );
        eventBus.publishCollateralOperationResult(
                operation.id(),
                operation.operationType().name(),
                "FAILED",
                operation.assetCode(),
                operation.referenceId(),
                OffsetDateTime.now().toString(),
                errorMessage,
                reasonCode
        );
        ensureReconciliation(operation, message, reasonCode, errorMessage);
        telegramAlertService.notifyCircuitOpened(
                "offline_pay.collateral.dead_letter",
                "operationId=" + operation.id()
                        + ", operationType=" + operation.operationType().name()
                        + ", reason=" + reasonCode
                        + ", error=" + errorMessage
        );
    }

    private void ensureReconciliation(
            CollateralOperation operation,
            SettlementBatchEventBus.QueuedCollateralMessage message,
            String reasonCode,
            String errorMessage
    ) {
        String caseType = resolveFailureCaseType(operation.operationType());
        if (reconciliationCaseRepository.findOpenByBatchIdAndCaseType(operation.id(), caseType).isPresent()) {
            return;
        }
        reconciliationCaseRepository.save(
                null,
                operation.id(),
                null,
                operation.collateralId(),
                caseType,
                ReconciliationCaseStatus.OPEN,
                reasonCode,
                jsonService.write(new LinkedHashMap<>(Map.of(
                        "operationId", operation.id(),
                        "operationType", operation.operationType().name(),
                        "referenceId", operation.referenceId(),
                        "assetCode", operation.assetCode(),
                        "retryable", true,
                        "nextAction", "RETRY_COLLATERAL_SYNC",
                        "syncTarget", "COIN_MANAGE_COLLATERAL",
                        "retryCount", message.attempts(),
                        "nextRetryAt", OffsetDateTime.now().plusMinutes(5).toString(),
                        "errorMessage", errorMessage
                )))
        );
    }

    private void resolveReconciliation(CollateralOperation operation, String caseType, String resolutionType) {
        reconciliationCaseRepository.findOpenByBatchIdAndCaseType(operation.id(), caseType)
                .ifPresent(existing -> reconciliationCaseRepository.resolve(
                        existing.id(),
                        jsonService.write(Map.of(
                                "resolvedAt", OffsetDateTime.now().toString(),
                                "resolutionType", resolutionType,
                                "resolvedByEventType", "COLLATERAL_RESULT"
                        ))
                ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        com.fasterxml.jackson.databind.JsonNode root = jsonService.readTree(metadataJson);
        java.util.LinkedHashMap<String, Object> parsed = new java.util.LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> {
            com.fasterxml.jackson.databind.JsonNode value = entry.getValue();
            if (value.isNumber()) {
                parsed.put(entry.getKey(), value.numberValue());
            } else if (value.isTextual()) {
                parsed.put(entry.getKey(), value.asText());
            } else if (value.isBoolean()) {
                parsed.put(entry.getKey(), value.asBoolean());
            } else if (value.isObject() || value.isArray()) {
                parsed.put(entry.getKey(), jsonService.readTree(value.toString()));
            }
        });
        return parsed;
    }

    private int readInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String readString(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private String resolveFailureReasonCode(CollateralOperationType operationType) {
        return operationType == CollateralOperationType.RELEASE
                ? OfflinePayReasonCode.COLLATERAL_RELEASE_FAIL
                : OfflinePayReasonCode.COLLATERAL_LOCK_FAIL;
    }

    private String resolveFailureCaseType(CollateralOperationType operationType) {
        return operationType == CollateralOperationType.RELEASE
                ? "COLLATERAL_RELEASE_FAILED"
                : "COLLATERAL_LOCK_FAILED";
    }
}
