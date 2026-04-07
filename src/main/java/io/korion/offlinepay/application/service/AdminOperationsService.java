package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.factory.SettlementBatchFactory;
import io.korion.offlinepay.application.factory.SettlementStreamEventFactory;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.application.port.OfflineSagaRepository;
import io.korion.offlinepay.application.port.OfflineWorkflowStateRepository;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.port.SettlementBatchRepository;
import io.korion.offlinepay.application.port.SettlementConflictRepository;
import io.korion.offlinepay.application.port.SettlementOutboxEventRepository;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.OfflineSaga;
import io.korion.offlinepay.domain.model.OfflineWorkflowState;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementConflict;
import io.korion.offlinepay.domain.model.SettlementConflictMetric;
import io.korion.offlinepay.domain.model.SettlementOutboxEvent;
import io.korion.offlinepay.domain.model.SettlementStatusMetric;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationsService {

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementConflictRepository settlementConflictRepository;
    private final CollateralOperationRepository collateralOperationRepository;
    private final DeviceRepository deviceRepository;
    private final OfflineEventLogRepository offlineEventLogRepository;
    private final OfflineWorkflowStateRepository offlineWorkflowStateRepository;
    private final OfflineSagaRepository offlineSagaRepository;
    private final OfflinePaymentProofRepository offlinePaymentProofRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final SettlementOutboxEventRepository settlementOutboxEventRepository;
    private final SettlementBatchEventBus settlementBatchEventBus;
    private final SettlementBatchFactory settlementBatchFactory;
    private final SettlementStreamEventFactory settlementStreamEventFactory;
    private final JsonService jsonService;

    public AdminOperationsService(
            SettlementBatchRepository settlementBatchRepository,
            SettlementConflictRepository settlementConflictRepository,
            CollateralOperationRepository collateralOperationRepository,
            DeviceRepository deviceRepository,
            OfflineEventLogRepository offlineEventLogRepository,
            OfflineWorkflowStateRepository offlineWorkflowStateRepository,
            OfflineSagaRepository offlineSagaRepository,
            OfflinePaymentProofRepository offlinePaymentProofRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            SettlementOutboxEventRepository settlementOutboxEventRepository,
            SettlementBatchEventBus settlementBatchEventBus,
            SettlementBatchFactory settlementBatchFactory,
            SettlementStreamEventFactory settlementStreamEventFactory,
            JsonService jsonService
    ) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementConflictRepository = settlementConflictRepository;
        this.collateralOperationRepository = collateralOperationRepository;
        this.deviceRepository = deviceRepository;
        this.offlineEventLogRepository = offlineEventLogRepository;
        this.offlineWorkflowStateRepository = offlineWorkflowStateRepository;
        this.offlineSagaRepository = offlineSagaRepository;
        this.offlinePaymentProofRepository = offlinePaymentProofRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.settlementOutboxEventRepository = settlementOutboxEventRepository;
        this.settlementBatchEventBus = settlementBatchEventBus;
        this.settlementBatchFactory = settlementBatchFactory;
        this.settlementStreamEventFactory = settlementStreamEventFactory;
        this.jsonService = jsonService;
    }

    @Transactional(readOnly = true)
    public List<SettlementConflict> listConflicts(
            String status,
            String conflictType,
            String collateralId,
            String deviceId,
            String networkScope,
            int size
    ) {
        return settlementConflictRepository.findRecent(
                status,
                conflictType,
                collateralId,
                deviceId,
                normalizeNetworkScope(networkScope),
                size
        );
    }

    @Transactional(readOnly = true)
    public List<SettlementBatch> listDeadLetterBatches(int size, String networkScope) {
        return settlementBatchRepository.findDeadLetterBatches(size, normalizeNetworkScope(networkScope));
    }

    @Transactional(readOnly = true)
    public List<SettlementBatch> listRecentBatches(int size, String networkScope) {
        return settlementBatchRepository.findRecentBatches(size, normalizeNetworkScope(networkScope));
    }

    @Transactional(readOnly = true)
    public List<CollateralOperation> listCollateralOperations(
            int size,
            String operationType,
            String status,
            String assetCode
    ) {
        return collateralOperationRepository.findRecent(
                size,
                parseOperationType(operationType),
                parseOperationStatus(status),
                assetCode
        );
    }

    @Transactional
    public SettlementBatch retryDeadLetterBatch(String batchId) {
        SettlementBatch batch = settlementBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("settlement batch not found: " + batchId));

        settlementBatchRepository.updateStatus(
                batch.id(),
                SettlementBatchStatus.UPLOADED,
                null,
                settlementBatchFactory.failureSummary(0, "manual retry requested", "")
        );
        SettlementStreamEventFactory.RequestedBatchEvent requestedBatchEvent = settlementStreamEventFactory
                .requestedBatchEvent(batch.id(), "ADMIN_RETRY", batch.sourceDeviceId());
        settlementBatchEventBus.publishBatchRequested(
                requestedBatchEvent.batchId(),
                requestedBatchEvent.uploaderType(),
                requestedBatchEvent.uploaderDeviceId(),
                requestedBatchEvent.requestedAt()
        );
        return settlementBatchRepository.findById(batch.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<SettlementOutboxEvent> listOutboxEvents(int size, String eventType, String status) {
        return settlementOutboxEventRepository.findRecent(size, eventType, status);
    }

    @Transactional
    public SettlementOutboxEvent retryDeadLetterOutboxEvent(String eventId) {
        SettlementOutboxEvent event = settlementOutboxEventRepository.findById(eventId);
        if (!"DEAD_LETTER".equals(event.status())) {
            throw new IllegalArgumentException("outbox event is not dead-lettered: " + eventId);
        }
        if (!isRetryableOutboxEvent(event.eventType())) {
            throw new IllegalArgumentException("outbox event type is not retryable: " + event.eventType());
        }
        return settlementOutboxEventRepository.requeueDeadLetter(eventId);
    }

    @Transactional
    public CollateralOperation retryCollateralOperation(String operationId) {
        CollateralOperation operation = collateralOperationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("collateral operation not found: " + operationId));
        settlementBatchEventBus.publishCollateralOperationRequested(
                operation.id(),
                operation.operationType().name(),
                operation.assetCode(),
                operation.referenceId(),
                OffsetDateTime.now().toString()
        );
        return collateralOperationRepository.findById(operation.id()).orElseThrow();
    }

    @Transactional
    public ReconciliationCase retryReconciliationCase(String caseId) {
        ReconciliationCase reconciliationCase = reconciliationCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("reconciliation case not found: " + caseId));
        var detail = jsonService.readTree(reconciliationCase.detailJson());
        if (!detail.path("retryable").asBoolean(false)) {
            throw new IllegalArgumentException("reconciliation case is not retryable: " + caseId);
        }
        String nextAction = detail.path("nextAction").asText("");
        if ("RETRY_EXTERNAL_SYNC".equals(nextAction)) {
            String eventType = detail.path("eventType").asText("");
            String payloadJson = detail.path("payloadJson").asText("");
            settlementBatchEventBus.publishExternalSyncRequested(
                    eventType,
                    reconciliationCase.settlementId(),
                    reconciliationCase.batchId(),
                    reconciliationCase.proofId(),
                    payloadJson,
                    OffsetDateTime.now().toString()
            );
        } else if ("RETRY_COLLATERAL_SYNC".equals(nextAction)) {
            settlementBatchEventBus.publishCollateralOperationRequested(
                    detail.path("operationId").asText(),
                    detail.path("operationType").asText(),
                    detail.path("assetCode").asText(),
                    detail.path("referenceId").asText(),
                    OffsetDateTime.now().toString()
            );
        } else {
            throw new IllegalArgumentException("unsupported reconciliation retry action: " + nextAction);
        }
        reconciliationCaseRepository.updateDetail(caseId, markManualRetry(detail));
        return reconciliationCaseRepository.findById(caseId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getOutboxOverview() {
        return Map.ofEntries(
                Map.entry("pending", settlementOutboxEventRepository.countByStatus("PENDING")),
                Map.entry("processing", settlementOutboxEventRepository.countByStatus("PROCESSING")),
                Map.entry("completed", settlementOutboxEventRepository.countByStatus("COMPLETED")),
                Map.entry("deadLetter", settlementOutboxEventRepository.countByStatus("DEAD_LETTER")),
                Map.entry("batchRequested", settlementOutboxEventRepository.countByEventType("BATCH_REQUESTED")),
                Map.entry("batchResult", settlementOutboxEventRepository.countByEventType("BATCH_RESULT")),
                Map.entry("conflict", settlementOutboxEventRepository.countByEventType("CONFLICT")),
                Map.entry("collateralRequested", settlementOutboxEventRepository.countByEventType("COLLATERAL_REQUESTED")),
                Map.entry("collateralResult", settlementOutboxEventRepository.countByEventType("COLLATERAL_RESULT")),
                Map.entry("ledgerSyncRequested", settlementOutboxEventRepository.countByEventType("LEDGER_SYNC_REQUESTED")),
                Map.entry("historySyncRequested", settlementOutboxEventRepository.countByEventType("HISTORY_SYNC_REQUESTED")),
                Map.entry("externalSyncDeadLetter", settlementOutboxEventRepository.countByEventType("EXTERNAL_SYNC_DEAD_LETTER"))
        );
    }

    private boolean isRetryableOutboxEvent(String eventType) {
        return "BATCH_REQUESTED".equals(eventType)
                || "LEDGER_SYNC_REQUESTED".equals(eventType)
                || "HISTORY_SYNC_REQUESTED".equals(eventType)
                || "COLLATERAL_REQUESTED".equals(eventType);
    }

    private String markManualRetry(com.fasterxml.jackson.databind.JsonNode detail) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        detail.fields().forEachRemaining(entry -> merged.put(entry.getKey(), entry.getValue()));
        merged.put("lastManualRetryAt", OffsetDateTime.now().toString());
        merged.put("nextRetryAt", OffsetDateTime.now().plusMinutes(5).toString());
        return jsonService.write(merged);
    }

    @Transactional(readOnly = true)
    public SettlementDashboardMetrics getSettlementDashboardMetrics(int hours, String networkScope) {
        String normalizedNetworkScope = normalizeNetworkScope(networkScope);
        List<SettlementStatusMetric> settlementMetrics = settlementBatchRepository.summarizeStatusByHour(hours, normalizedNetworkScope);
        List<SettlementConflictMetric> conflictMetrics = settlementConflictRepository.summarizeByHour(hours, normalizedNetworkScope);

        Map<String, BucketAccumulator> buckets = new LinkedHashMap<>();
        for (SettlementStatusMetric metric : settlementMetrics) {
            BucketAccumulator accumulator = buckets.computeIfAbsent(
                    metric.bucketAt().toString(),
                    ignored -> new BucketAccumulator(metric.bucketAt().toString())
            );
            accumulator.put(metric.status(), metric.count());
        }
        for (SettlementConflictMetric metric : conflictMetrics) {
            BucketAccumulator accumulator = buckets.computeIfAbsent(
                    metric.bucketAt().toString(),
                    ignored -> new BucketAccumulator(metric.bucketAt().toString())
            );
            accumulator.conflicts = metric.count();
        }

        List<SettlementTimeseriesBucket> items = buckets.values().stream()
                .map(BucketAccumulator::toBucket)
                .toList();
        return new SettlementDashboardMetrics(hours, items);
    }

    @Transactional(readOnly = true)
    public OfflinePayOverview getOfflinePayOverview(int days, String networkScope) {
        int hours = Math.max(1, days * 24);
        String normalizedNetworkScope = normalizeNetworkScope(networkScope);
        SettlementDashboardMetrics metrics = getSettlementDashboardMetrics(hours, normalizedNetworkScope);
        List<SettlementBatch> recentBatches = settlementBatchRepository.findRecentBatches(Math.min(days, 8), normalizedNetworkScope);

        long requestedBatchCount = 0L;
        long settledBatchCount = 0L;
        long conflictBatchCount = 0L;
        long pendingBatchCount = 0L;
        long proofCountTotal = 0L;
        for (SettlementTimeseriesBucket bucket : metrics.items()) {
            requestedBatchCount += bucket.created() + bucket.uploaded() + bucket.validating() + bucket.partiallySettled() + bucket.settled() + bucket.failed() + bucket.closed();
            settledBatchCount += bucket.settled();
            conflictBatchCount += bucket.conflicts();
            pendingBatchCount += bucket.created() + bucket.uploaded() + bucket.validating();
        }
        for (SettlementBatch recentBatch : recentBatches) {
            proofCountTotal += recentBatch.proofsCount();
        }

        return new OfflinePayOverview(
                new OfflinePayOverviewSummary(
                        requestedBatchCount,
                        settledBatchCount,
                        conflictBatchCount,
                        settlementBatchRepository.countDeadLetterBatches(hours, normalizedNetworkScope),
                        pendingBatchCount,
                        recentBatches.isEmpty() ? 0D : (double) proofCountTotal / recentBatches.size()
                ),
                metrics.items().stream()
                        .map(bucket -> new OfflinePayDailyMetric(
                                bucket.bucketAt(),
                                bucket.created() + bucket.uploaded() + bucket.validating() + bucket.partiallySettled() + bucket.settled() + bucket.failed() + bucket.closed(),
                                bucket.settled(),
                                bucket.conflicts(),
                                bucket.failed(),
                                bucket.created() + bucket.uploaded() + bucket.validating() + bucket.partiallySettled() + bucket.settled() + bucket.failed() + bucket.closed()
                        ))
                        .toList(),
                recentBatches.stream()
                        .map(batch -> new OfflinePayRecentBatch(
                                batch.id(),
                                batch.createdAt(),
                                batch.updatedAt(),
                                batch.status().name(),
                                batch.proofsCount(),
                                0L,
                                0
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public BatchOverview getBatchOverview(int days, String networkScope) {
        OfflinePayOverview overview = getOfflinePayOverview(days, networkScope);
        return new BatchOverview(overview.summary(), overview.timeline(), overview.recentBatches());
    }

    @Transactional(readOnly = true)
    public CollateralOperationOverview getCollateralOperationOverview(int size, String assetCode) {
        List<CollateralOperation> recentOperations = collateralOperationRepository.findRecent(size, null, null, assetCode);
        long pendingCount = recentOperations.stream().filter(item -> item.status() == CollateralOperationStatus.REQUESTED).count();
        long failedCount = recentOperations.stream().filter(item -> item.status() == CollateralOperationStatus.FAILED).count();
        long completedCount = recentOperations.stream().filter(item -> item.status() == CollateralOperationStatus.COMPLETED).count();
        long topupCount = recentOperations.stream().filter(item -> item.operationType() == CollateralOperationType.TOPUP).count();
        long releaseCount = recentOperations.stream().filter(item -> item.operationType() == CollateralOperationType.RELEASE).count();

        return new CollateralOperationOverview(
                new CollateralOperationOverviewSummary(
                        pendingCount,
                        failedCount,
                        completedCount,
                        topupCount,
                        releaseCount
                ),
                recentOperations
        );
    }

    @Transactional(readOnly = true)
    public List<OfflineEventLog> listOfflineEvents(
            int size,
            String eventType,
            String eventStatus,
            String assetCode
    ) {
        return offlineEventLogRepository.findRecent(
                size,
                parseOfflineEventType(eventType),
                parseOfflineEventStatus(eventStatus),
                assetCode
        );
    }

    @Transactional(readOnly = true)
    public List<OfflineWorkflowState> listWorkflowStates(int size, String workflowType, String workflowStage) {
        return offlineWorkflowStateRepository.findRecent(size, workflowType, workflowStage);
    }

    @Transactional(readOnly = true)
    public List<OfflineSaga> listSagas(int size, String sagaType, String status) {
        return offlineSagaRepository.findRecent(
                size,
                parseOfflineSagaType(sagaType),
                parseOfflineSagaStatus(status)
        );
    }

    @Transactional(readOnly = true)
    public List<ReconciliationCase> listReconciliationCases(
            int size,
            String status,
            String caseType,
            String reasonCode
    ) {
        return reconciliationCaseRepository.findRecent(
                size,
                parseReconciliationCaseStatus(status),
                caseType,
                reasonCode
        );
    }

    @Transactional(readOnly = true)
    public List<ReconciliationCaseView> listReconciliationCaseViews(
            int size,
            String status,
            String caseType,
            String reasonCode
    ) {
        return listReconciliationCases(size, status, caseType, reasonCode)
                .stream()
                .map(item -> new ReconciliationCaseView(
                        item,
                        item.settlementId() == null || item.settlementId().isBlank()
                                ? null
                                : offlineSagaRepository.findBySagaTypeAndReferenceId(OfflineSagaType.SETTLEMENT, item.settlementId())
                                        .orElse(null)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getAnomalyOverview(int sampleSize) {
        int limit = Math.max(20, sampleSize);
        List<SettlementConflict> conflicts = settlementConflictRepository.findRecent(null, null, null, null, null, limit);
        List<ReconciliationCase> reconciliationCases = reconciliationCaseRepository.findRecent(limit, null, null, null);
        List<OfflineWorkflowState> workflowStates = offlineWorkflowStateRepository.findRecent(limit, null, null);
        List<OfflineSaga> sagas = offlineSagaRepository.findRecent(limit, null, null);

        long duplicateReplayConflictCount = conflicts.stream()
                .filter(item -> containsDuplicateReplaySignal(item.conflictType()))
                .count();
        long duplicateReplayCaseCount = reconciliationCases.stream()
                .filter(item -> containsDuplicateReplaySignal(item.caseType()) || containsDuplicateReplaySignal(item.reasonCode()))
                .count();
        long duplicateReplayWorkflowCount = workflowStates.stream()
                .filter(item -> containsDuplicateReplaySignal(item.reasonCode()) || containsDuplicateReplaySignal(item.errorMessage()))
                .count();

        long failedWorkflowCount = workflowStates.stream()
                .filter(item -> "FAILED".equals(item.workflowStage()) || "DEAD_LETTERED".equals(item.workflowStage()))
                .count();
        long blockedSagaCount = sagas.stream()
                .filter(item ->
                        item.status() == OfflineSagaStatus.FAILED
                                || item.status() == OfflineSagaStatus.DEAD_LETTERED
                                || item.status() == OfflineSagaStatus.COMPENSATION_REQUIRED
                )
                .count();
        long openReconciliationCount = reconciliationCases.stream()
                .filter(item -> item.status() != ReconciliationCaseStatus.RESOLVED)
                .count();

        return Map.of(
                "sampleSize", (long) limit,
                "duplicateReplayConflictCount", duplicateReplayConflictCount,
                "duplicateReplayCaseCount", duplicateReplayCaseCount,
                "duplicateReplayWorkflowCount", duplicateReplayWorkflowCount,
                "duplicateReplaySignalCount", duplicateReplayConflictCount + duplicateReplayCaseCount + duplicateReplayWorkflowCount,
                "failedWorkflowCount", failedWorkflowCount,
                "blockedSagaCount", blockedSagaCount,
                "openReconciliationCount", openReconciliationCount,
                "conflictCount", (long) conflicts.size()
        );
    }

    private boolean containsDuplicateReplaySignal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toUpperCase();
        return normalized.contains("DUPLICATE")
                || normalized.contains("REPLAY")
                || normalized.contains("PAYLOAD_MISMATCH");
    }

    @Transactional(readOnly = true)
    public OfflineEventOverview getOfflineEventOverview(int size, String assetCode) {
        List<OfflineEventLog> recentEvents = offlineEventLogRepository.findRecent(size, null, null, assetCode);
        long pendingCount = recentEvents.stream().filter(item -> item.eventStatus() == OfflineEventStatus.PENDING).count();
        long failedCount = recentEvents.stream().filter(item -> item.eventStatus() == OfflineEventStatus.FAILED).count();
        long acknowledgedCount = recentEvents.stream().filter(item -> item.eventStatus() == OfflineEventStatus.ACKNOWLEDGED).count();
        long syncFailedCount = recentEvents.stream().filter(item ->
                item.eventType() == OfflineEventType.SYNC_FAILED
                        || item.eventType() == OfflineEventType.BATCH_SYNC_FAIL
                        || item.eventType() == OfflineEventType.LOCAL_QUEUE_SAVE_FAIL
                        || item.eventType() == OfflineEventType.SERVER_VALIDATION_FAIL
                        || item.eventType() == OfflineEventType.SETTLEMENT_FAIL
                        || item.eventType() == OfflineEventType.SETTLEMENT_FAILED
        ).count();
        long rejectedCount = recentEvents.stream().filter(item ->
                item.eventType() == OfflineEventType.REQUEST_REJECTED
                        || item.eventType() == OfflineEventType.RECEIVE_REJECTED
        ).count();
        long cancelledCount = recentEvents.stream().filter(item ->
                item.eventType() == OfflineEventType.REQUEST_CANCELLED
                        || item.eventType() == OfflineEventType.AUTH_CANCELLED
        ).count();
        long connectionFailedCount = recentEvents.stream().filter(item ->
                item.eventType() == OfflineEventType.NFC_CONNECT_FAIL
                        || item.eventType() == OfflineEventType.BLE_SCAN_FAIL
                        || item.eventType() == OfflineEventType.BLE_PAIR_FAIL
                        || item.eventType() == OfflineEventType.TRANSPORT_FAILED
        ).count();
        long authFailedCount = recentEvents.stream().filter(item ->
                item.eventType() == OfflineEventType.AUTH_BIOMETRIC_FAIL
                        || item.eventType() == OfflineEventType.AUTH_PIN_FAIL
        ).count();
        long payloadFailedCount = recentEvents.stream().filter(item ->
                item.eventType() == OfflineEventType.QR_PARSE_FAIL
                        || item.eventType() == OfflineEventType.PROOF_NOT_FOUND
                        || item.eventType() == OfflineEventType.PROOF_EXPIRED
                        || item.eventType() == OfflineEventType.PROOF_TAMPERED
                        || item.eventType() == OfflineEventType.PAYLOAD_BUILD_FAIL
        ).count();

        return new OfflineEventOverview(
                new OfflineEventOverviewSummary(
                        pendingCount,
                        failedCount,
                        acknowledgedCount,
                        syncFailedCount,
                        rejectedCount,
                        cancelledCount,
                        connectionFailedCount,
                        authFailedCount,
                        payloadFailedCount
                ),
                recentEvents
        );
    }

    @Transactional(readOnly = true)
    public List<OfflinePaymentProof> listProofs(int size, String status, String channelType) {
        return offlinePaymentProofRepository.findRecent(
                size,
                parseProofStatus(status),
                channelType == null || channelType.isBlank() ? null : channelType.trim().toUpperCase()
        );
    }

    @Transactional(readOnly = true)
    public ProofOverview getProofOverview(int size, String channelType) {
        List<OfflinePaymentProof> recentProofs = offlinePaymentProofRepository.findRecent(
                size,
                null,
                channelType == null || channelType.isBlank() ? null : channelType.trim().toUpperCase()
        );
        long uploadedCount = recentProofs.stream().filter(item -> item.status() == OfflineProofStatus.UPLOADED).count();
        long validatingCount = recentProofs.stream().filter(item -> item.status() == OfflineProofStatus.VALIDATING).count();
        long verifiedCount = recentProofs.stream().filter(item -> item.status() == OfflineProofStatus.VERIFIED_OFFLINE).count();
        long settledCount = recentProofs.stream().filter(item -> item.status() == OfflineProofStatus.SETTLED).count();
        long rejectedCount = recentProofs.stream().filter(item -> item.status() == OfflineProofStatus.REJECTED).count();
        long conflictedCount = recentProofs.stream().filter(item -> item.status() == OfflineProofStatus.CONFLICTED).count();
        long failedCount = recentProofs.stream().filter(item ->
                item.status() == OfflineProofStatus.FAILED || item.status() == OfflineProofStatus.EXPIRED
        ).count();
        return new ProofOverview(
                new ProofOverviewSummary(
                        uploadedCount,
                        validatingCount,
                        verifiedCount,
                        settledCount,
                        rejectedCount,
                        conflictedCount,
                        failedCount
                ),
                recentProofs
        );
    }

    @Transactional(readOnly = true)
    public List<Device> listDevices(int size, String status) {
        return deviceRepository.findRecent(size, parseDeviceStatus(status));
    }

    @Transactional(readOnly = true)
    public DeviceOverview getDeviceOverview(int size) {
        List<Device> recentDevices = deviceRepository.findRecent(size, null);
        long activeCount = recentDevices.stream().filter(item -> item.status() == DeviceStatus.ACTIVE).count();
        long revokedCount = recentDevices.stream().filter(item -> item.status() == DeviceStatus.REVOKED).count();
        long frozenCount = recentDevices.stream().filter(item -> item.status() == DeviceStatus.FROZEN).count();
        return new DeviceOverview(
                new DeviceOverviewSummary(activeCount, revokedCount, frozenCount),
                recentDevices
        );
    }

    private String normalizeNetworkScope(String networkScope) {
        if (networkScope == null || networkScope.isBlank()) {
            return null;
        }

        String normalized = networkScope.trim().toLowerCase();
        return switch (normalized) {
            case "mainnet", "testnet" -> normalized;
            default -> null;
        };
    }

    public record SettlementDashboardMetrics(
            int hours,
            List<SettlementTimeseriesBucket> items
    ) {}

    public record OfflinePayOverview(
            OfflinePayOverviewSummary summary,
            List<OfflinePayDailyMetric> timeline,
            List<OfflinePayRecentBatch> recentBatches
    ) {}

    public record OfflinePayOverviewSummary(
            long requestedBatchCount,
            long settledBatchCount,
            long conflictBatchCount,
            long deadLetterBatchCount,
            long pendingBatchCount,
            double averageProofCount
    ) {}

    public record OfflinePayDailyMetric(
            String snapshotDate,
            long requestedBatchCount,
            long settledBatchCount,
            long conflictBatchCount,
            long deadLetterBatchCount,
            long proofCount
    ) {}

    public record OfflinePayRecentBatch(
            String batchId,
            OffsetDateTime requestedAt,
            OffsetDateTime finalizedAt,
            String status,
            int proofCount,
            long conflictCount,
            int attemptCount
    ) {}

    public record SettlementTimeseriesBucket(
            String bucketAt,
            long created,
            long uploaded,
            long validating,
            long partiallySettled,
            long settled,
            long failed,
            long closed,
            long conflicts
    ) {}

    public record CollateralOperationOverview(
            CollateralOperationOverviewSummary summary,
            List<CollateralOperation> recentOperations
    ) {}

    public record BatchOverview(
            OfflinePayOverviewSummary summary,
            List<OfflinePayDailyMetric> timeline,
            List<OfflinePayRecentBatch> recentBatches
    ) {}

    public record CollateralOperationOverviewSummary(
            long pendingCount,
            long failedCount,
            long completedCount,
            long topupCount,
            long releaseCount
    ) {}

    public record OfflineEventOverview(
            OfflineEventOverviewSummary summary,
            List<OfflineEventLog> recentEvents
    ) {}

    public record ProofOverview(
            ProofOverviewSummary summary,
            List<OfflinePaymentProof> recentProofs
    ) {}

    public record DeviceOverview(
            DeviceOverviewSummary summary,
            List<Device> recentDevices
    ) {}

    public record OfflineEventOverviewSummary(
            long pendingCount,
            long failedCount,
            long acknowledgedCount,
            long syncFailedCount,
            long rejectedCount,
            long cancelledCount,
            long connectionFailedCount,
            long authFailedCount,
            long payloadFailedCount
    ) {}

    public record ProofOverviewSummary(
            long uploadedCount,
            long validatingCount,
            long verifiedCount,
            long settledCount,
            long rejectedCount,
            long conflictedCount,
            long failedCount
    ) {}

    public record DeviceOverviewSummary(
            long activeCount,
            long revokedCount,
            long frozenCount
    ) {}

    public record ReconciliationCaseView(
            ReconciliationCase reconciliationCase,
            OfflineSaga settlementSaga
    ) {}

    private CollateralOperationType parseOperationType(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return null;
        }
        try {
            return CollateralOperationType.valueOf(operationType.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private CollateralOperationStatus parseOperationStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CollateralOperationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private OfflineEventType parseOfflineEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        try {
            return OfflineEventType.valueOf(eventType.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ReconciliationCaseStatus parseReconciliationCaseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReconciliationCaseStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private OfflineSagaType parseOfflineSagaType(String sagaType) {
        if (sagaType == null || sagaType.isBlank()) {
            return null;
        }
        try {
            return OfflineSagaType.valueOf(sagaType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported sagaType: " + sagaType);
        }
    }

    private OfflineSagaStatus parseOfflineSagaStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OfflineSagaStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported saga status: " + status);
        }
    }

    private OfflineEventStatus parseOfflineEventStatus(String eventStatus) {
        if (eventStatus == null || eventStatus.isBlank()) {
            return null;
        }
        try {
            return OfflineEventStatus.valueOf(eventStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private OfflineProofStatus parseProofStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OfflineProofStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private DeviceStatus parseDeviceStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DeviceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final class BucketAccumulator {
        private final String bucketAt;
        private long created;
        private long uploaded;
        private long validating;
        private long partiallySettled;
        private long settled;
        private long failed;
        private long closed;
        private long conflicts;

        private BucketAccumulator(String bucketAt) {
            this.bucketAt = bucketAt;
        }

        private void put(String status, long count) {
            switch (status) {
                case "CREATED" -> this.created = count;
                case "UPLOADED" -> this.uploaded = count;
                case "VALIDATING" -> this.validating = count;
                case "PARTIALLY_SETTLED" -> this.partiallySettled = count;
                case "SETTLED" -> this.settled = count;
                case "FAILED" -> this.failed = count;
                case "CLOSED" -> this.closed = count;
                default -> {
                }
            }
        }

        private SettlementTimeseriesBucket toBucket() {
            return new SettlementTimeseriesBucket(
                    bucketAt,
                    created,
                    uploaded,
                    validating,
                    partiallySettled,
                    settled,
                    failed,
                    closed,
                    conflicts
            );
        }
    }
}
