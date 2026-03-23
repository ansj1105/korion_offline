package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.factory.SettlementBatchFactory;
import io.korion.offlinepay.application.factory.SettlementStreamEventFactory;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.port.SettlementBatchRepository;
import io.korion.offlinepay.application.port.SettlementConflictRepository;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementConflict;
import io.korion.offlinepay.domain.model.SettlementConflictMetric;
import io.korion.offlinepay.domain.model.SettlementStatusMetric;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
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
    private final OfflinePaymentProofRepository offlinePaymentProofRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final SettlementBatchEventBus settlementBatchEventBus;
    private final SettlementBatchFactory settlementBatchFactory;
    private final SettlementStreamEventFactory settlementStreamEventFactory;

    public AdminOperationsService(
            SettlementBatchRepository settlementBatchRepository,
            SettlementConflictRepository settlementConflictRepository,
            CollateralOperationRepository collateralOperationRepository,
            DeviceRepository deviceRepository,
            OfflineEventLogRepository offlineEventLogRepository,
            OfflinePaymentProofRepository offlinePaymentProofRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            SettlementBatchEventBus settlementBatchEventBus,
            SettlementBatchFactory settlementBatchFactory,
            SettlementStreamEventFactory settlementStreamEventFactory
    ) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementConflictRepository = settlementConflictRepository;
        this.collateralOperationRepository = collateralOperationRepository;
        this.deviceRepository = deviceRepository;
        this.offlineEventLogRepository = offlineEventLogRepository;
        this.offlinePaymentProofRepository = offlinePaymentProofRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.settlementBatchEventBus = settlementBatchEventBus;
        this.settlementBatchFactory = settlementBatchFactory;
        this.settlementStreamEventFactory = settlementStreamEventFactory;
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
            requestedBatchCount += bucket.created + bucket.uploaded + bucket.validating + bucket.partiallySettled + bucket.settled + bucket.failed + bucket.closed;
            settledBatchCount += bucket.settled;
            conflictBatchCount += bucket.conflicts;
            pendingBatchCount += bucket.created + bucket.uploaded + bucket.validating;
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
                                bucket.created + bucket.uploaded + bucket.validating + bucket.partiallySettled + bucket.settled + bucket.failed + bucket.closed,
                                bucket.settled,
                                bucket.conflicts,
                                bucket.failed,
                                bucket.created + bucket.uploaded + bucket.validating + bucket.partiallySettled + bucket.settled + bucket.failed + bucket.closed
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
