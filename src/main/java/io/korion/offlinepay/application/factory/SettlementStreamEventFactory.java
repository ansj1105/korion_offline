package io.korion.offlinepay.application.factory;

import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class SettlementStreamEventFactory {

    public RequestedBatchEvent requestedBatchEvent(String batchId, String uploaderType, String uploaderDeviceId) {
        return new RequestedBatchEvent(
                batchId,
                uploaderType,
                uploaderDeviceId,
                OffsetDateTime.now().toString()
        );
    }

    public BatchResultEvent batchResultEvent(
            String batchId,
            SettlementBatchStatus status,
            int settledCount,
            int failedCount
    ) {
        return new BatchResultEvent(
                batchId,
                status.name(),
                settledCount,
                failedCount,
                OffsetDateTime.now().toString()
        );
    }

    public ConflictEvent conflictEvent(
            SettlementRequest request,
            OfflinePaymentProof proof,
            String conflictType
    ) {
        return new ConflictEvent(
                request.batchId(),
                proof.voucherId(),
                proof.collateralId(),
                conflictType,
                "HIGH",
                OffsetDateTime.now().toString()
        );
    }

    public DeadLetterEvent deadLetterEvent(String batchId, int attemptCount, String errorMessage) {
        return new DeadLetterEvent(
                batchId,
                attemptCount,
                errorMessage,
                OffsetDateTime.now().toString()
        );
    }

    public record RequestedBatchEvent(
            String batchId,
            String uploaderType,
            String uploaderDeviceId,
            String requestedAt
    ) {}

    public record BatchResultEvent(
            String batchId,
            String status,
            int settledCount,
            int failedCount,
            String processedAt
    ) {}

    public record ConflictEvent(
            String batchId,
            String voucherId,
            String collateralId,
            String conflictType,
            String severity,
            String createdAt
    ) {}

    public record DeadLetterEvent(
            String batchId,
            int attemptCount,
            String errorMessage,
            String failedAt
    ) {}
}
