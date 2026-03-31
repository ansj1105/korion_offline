package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.policy.OfflineFailureClass;
import io.korion.offlinepay.domain.policy.OfflineFailurePolicy;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final OfflineSagaService offlineSagaService;
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
            OfflineSagaService offlineSagaService,
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
        this.offlineSagaService = offlineSagaService;
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
                if (isTerminalFailure(exception) || message.attempts() >= properties.worker().maxAttempts()) {
                    markDeadLetter(message, exception);
                    eventBus.acknowledgeCollateral(message.messageId());
                } else {
                    eventBus.requeueCollateral(
                            message.messageId(),
                            resolveFailureReasonCodeName(message.operationType()),
                            summarize(exception)
                    );
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
        offlineSagaService.markProcessing(
                resolveSagaType(operation),
                operation.id(),
                "SERVER_ACCEPTED",
                Map.of(
                        "operationId", operation.id(),
                        "operationType", operation.operationType().name(),
                        "assetCode", operation.assetCode(),
                        "referenceId", operation.referenceId()
                )
        );

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
        offlineSagaService.markCompleted(
                OfflineSagaType.COLLATERAL_TOPUP,
                operation.id(),
                "COLLATERAL_LOCKED",
                Map.of(
                        "operationId", operation.id(),
                        "referenceId", operation.referenceId(),
                        "collateralId", collateral.id(),
                        "assetCode", operation.assetCode()
                )
        );
        offlineSnapshotStreamService.publishCollateralChanged(
                operation.userId(),
                operation.deviceId(),
                operation.assetCode(),
                "TOPUP_COMPLETED"
        );
    }

    private void processRelease(SettlementBatchEventBus.QueuedCollateralMessage message, CollateralOperation operation) {
        List<CollateralLock> activeLocks = new ArrayList<>(collateralRepository.findActiveByUserIdAndAssetCode(
                operation.userId(),
                operation.assetCode()
        ));
        if (activeLocks.isEmpty()) {
            throw new IllegalArgumentException("no releasable collateral found");
        }
        if (operation.collateralId() != null && !operation.collateralId().isBlank()) {
            activeLocks.sort((left, right) -> {
                if (left.id().equals(operation.collateralId())) {
                    return -1;
                }
                if (right.id().equals(operation.collateralId())) {
                    return 1;
                }
                return left.createdAt().compareTo(right.createdAt());
            });
        }

        BigDecimal remainingToRelease = operation.amount();
        List<Map<String, Object>> releasedSegments = new ArrayList<>();
        int segmentIndex = 0;
        for (CollateralLock collateral : activeLocks) {
            if (remainingToRelease.signum() <= 0) {
                break;
            }
            BigDecimal releasable = collateral.remainingAmount().min(remainingToRelease);
            if (releasable.signum() <= 0) {
                continue;
            }

            String segmentReferenceId = operation.referenceId() + ":" + segmentIndex++;
            coinManageCollateralPort.releaseCollateral(
                    operation.userId(),
                    operation.deviceId(),
                    collateral.id(),
                    operation.assetCode(),
                    releasable,
                    segmentReferenceId
            );
            collateralRepository.deductLockedAndRemainingAmount(collateral.id(), releasable);
            boolean fullyReleased = collateral.remainingAmount().compareTo(releasable) <= 0;
            collateralRepository.updateStatus(
                    collateral.id(),
                    fullyReleased ? CollateralStatus.RELEASED : CollateralStatus.LOCKED,
                    jsonService.write(Map.of(
                            "referenceId", segmentReferenceId,
                            "releasedAmount", releasable,
                            "releasedAt", OffsetDateTime.now().toString()
                    ))
            );
            releasedSegments.add(Map.of(
                    "collateralId", collateral.id(),
                    "amount", releasable.toPlainString(),
                    "referenceId", segmentReferenceId
            ));
            remainingToRelease = remainingToRelease.subtract(releasable);
        }
        if (remainingToRelease.signum() > 0) {
            throw new IllegalArgumentException("no releasable quantity for requested amount");
        }

        collateralOperationRepository.markCompleted(
                operation.referenceId(),
                operation.collateralId(),
                jsonService.write(Map.of(
                        "status", "RELEASED",
                        "completedAt", OffsetDateTime.now().toString(),
                        "releasedSegments", releasedSegments
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
        offlineSagaService.markCompleted(
                OfflineSagaType.COLLATERAL_RELEASE,
                operation.id(),
                "COLLATERAL_RELEASED",
                Map.of(
                        "operationId", operation.id(),
                        "referenceId", operation.referenceId(),
                        "assetCode", operation.assetCode(),
                        "releasedSegments", releasedSegments
                )
        );
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
        OfflineFailureClass failureClass = OfflineFailurePolicy.classify(reasonCode, errorMessage);
        if (failureClass == OfflineFailureClass.TRANSPORT
                || failureClass == OfflineFailureClass.AUTH
                || failureClass == OfflineFailureClass.SYSTEM
                || failureClass == OfflineFailureClass.PARTIAL) {
            offlineSagaService.markDeadLettered(
                    resolveSagaType(operation),
                    operation.id(),
                    "DEAD_LETTERED",
                    reasonCode,
                    Map.of(
                            "operationId", operation.id(),
                            "operationType", operation.operationType().name(),
                            "reasonCode", reasonCode,
                            "errorMessage", errorMessage
                    )
            );
        } else {
            offlineSagaService.markFailed(
                    resolveSagaType(operation),
                    operation.id(),
                    "FAILED",
                    reasonCode,
                    Map.of(
                            "operationId", operation.id(),
                            "operationType", operation.operationType().name(),
                            "reasonCode", reasonCode,
                            "errorMessage", errorMessage
                    )
            );
        }
        String alertReason = "operationId=" + operation.id()
                + ", operationType=" + operation.operationType().name()
                + ", failureClass=" + failureClass.name()
                + ", reason=" + reasonCode
                + ", error=" + errorMessage;
        if (failureClass == OfflineFailureClass.TRANSPORT || failureClass == OfflineFailureClass.AUTH) {
            telegramAlertService.notifyDeadLetter("offline_pay.collateral.dead_letter", alertReason);
            return;
        }
        if (failureClass == OfflineFailureClass.SYSTEM || failureClass == OfflineFailureClass.PARTIAL) {
            telegramAlertService.notifyOperationalIssue("offline_pay.collateral.dead_letter", alertReason);
            return;
        }
        telegramAlertService.notifyDeadLetter("offline_pay.collateral.dead_letter", alertReason);
    }

    private boolean isTerminalFailure(RuntimeException exception) {
        String message = summarize(exception).toUpperCase();
        return message.contains("INSUFFICIENT_BALANCE")
                || message.contains("VALIDATION_ERROR")
                || message.contains("400 BAD REQUEST")
                || message.contains("404 NOT FOUND");
    }

    private String resolveFailureReasonCodeName(String operationType) {
        if (CollateralOperationType.RELEASE.name().equals(operationType)) {
            return OfflinePayReasonCode.COLLATERAL_RELEASE_FAIL;
        }
        return OfflinePayReasonCode.COLLATERAL_LOCK_FAIL;
    }

    private String summarize(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "unknown collateral sync failure"
                : exception.getMessage();
    }

    private void ensureReconciliation(
            CollateralOperation operation,
            SettlementBatchEventBus.QueuedCollateralMessage message,
            String reasonCode,
            String errorMessage
    ) {
        String caseType = resolveFailureCaseType(operation.operationType());
        if (reconciliationCaseRepository.findOpenByVoucherIdAndCaseType(operation.id(), caseType).isPresent()) {
            return;
        }
        OfflineFailureClass failureClass = OfflineFailurePolicy.classify(reasonCode, errorMessage);
        boolean retryable = OfflineFailurePolicy.isRetryable(failureClass);
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("operationId", operation.id());
        detail.put("operationType", operation.operationType().name());
        detail.put("referenceId", operation.referenceId());
        detail.put("assetCode", operation.assetCode());
        detail.put("failureClass", failureClass.name());
        detail.put("retryable", retryable);
        detail.put("adminAction", OfflineFailurePolicy.adminAction(failureClass));
        detail.put("nextAction", "RETRY_COLLATERAL_SYNC");
        detail.put("syncTarget", "COIN_MANAGE_COLLATERAL");
        detail.put("retryCount", message.attempts());
        detail.put(
                "nextRetryAt",
                retryable
                        ? OffsetDateTime.now()
                                .plus(OfflineFailurePolicy.nextRetryDelay(failureClass, message.attempts()))
                                .toString()
                        : ""
        );
        detail.put("errorMessage", errorMessage);
        reconciliationCaseRepository.save(
                null,
                null,
                null,
                operation.id(),
                caseType,
                ReconciliationCaseStatus.OPEN,
                reasonCode,
                jsonService.write(detail)
        );
    }

    private void resolveReconciliation(CollateralOperation operation, String caseType, String resolutionType) {
        reconciliationCaseRepository.findOpenByVoucherIdAndCaseType(operation.id(), caseType)
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

    private OfflineSagaType resolveSagaType(CollateralOperation operation) {
        return operation.operationType() == CollateralOperationType.RELEASE
                ? OfflineSagaType.COLLATERAL_RELEASE
                : OfflineSagaType.COLLATERAL_TOPUP;
    }
}
