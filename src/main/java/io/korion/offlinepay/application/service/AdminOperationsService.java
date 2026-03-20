package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.factory.SettlementBatchFactory;
import io.korion.offlinepay.application.factory.SettlementStreamEventFactory;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.port.SettlementBatchRepository;
import io.korion.offlinepay.application.port.SettlementConflictRepository;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementConflict;
import io.korion.offlinepay.domain.model.SettlementConflictMetric;
import io.korion.offlinepay.domain.model.SettlementStatusMetric;
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
    private final SettlementBatchEventBus settlementBatchEventBus;
    private final SettlementBatchFactory settlementBatchFactory;
    private final SettlementStreamEventFactory settlementStreamEventFactory;

    public AdminOperationsService(
            SettlementBatchRepository settlementBatchRepository,
            SettlementConflictRepository settlementConflictRepository,
            SettlementBatchEventBus settlementBatchEventBus,
            SettlementBatchFactory settlementBatchFactory,
            SettlementStreamEventFactory settlementStreamEventFactory
    ) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementConflictRepository = settlementConflictRepository;
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
            int size
    ) {
        return settlementConflictRepository.findRecent(status, conflictType, collateralId, deviceId, size);
    }

    @Transactional(readOnly = true)
    public List<SettlementBatch> listDeadLetterBatches(int size) {
        return settlementBatchRepository.findDeadLetterBatches(size);
    }

    @Transactional
    public SettlementBatch retryDeadLetterBatch(String batchId) {
        SettlementBatch batch = settlementBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("settlement batch not found: " + batchId));

        settlementBatchRepository.updateStatus(
                batch.id(),
                SettlementBatchStatus.UPLOADED,
                settlementBatchFactory.failureSummary(0, "manual retry requested")
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
    public SettlementDashboardMetrics getSettlementDashboardMetrics(int hours) {
        List<SettlementStatusMetric> settlementMetrics = settlementBatchRepository.summarizeStatusByHour(hours);
        List<SettlementConflictMetric> conflictMetrics = settlementConflictRepository.summarizeByHour(hours);

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
    public OfflinePayOverview getOfflinePayOverview(int days) {
        int hours = Math.max(1, days * 24);
        SettlementDashboardMetrics metrics = getSettlementDashboardMetrics(hours);
        List<SettlementBatch> recentBatches = settlementBatchRepository.findRecentBatches(Math.min(days, 8));

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
                        settlementBatchRepository.countDeadLetterBatches(hours),
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
