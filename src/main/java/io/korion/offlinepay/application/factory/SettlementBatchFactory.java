package io.korion.offlinepay.application.factory;

import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.application.service.SettlementApplicationService;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SettlementBatchFactory {

    private final JsonService jsonService;

    public SettlementBatchFactory(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public SettlementBatchDraft createDraft(SettlementApplicationService.SubmitSettlementBatchCommand command) {
        return new SettlementBatchDraft(
                command.uploaderDeviceId(),
                command.idempotencyKey(),
                SettlementBatchStatus.CREATED,
                command.proofs().size(),
                jsonService.write(Map.of(
                        "submittedAt", OffsetDateTime.now().toString(),
                        "uploaderType", command.uploaderType().name(),
                        "triggerMode", command.triggerMode() != null ? command.triggerMode() : "MANUAL"
                ))
        );
    }

    public String uploadedSummary(Iterable<String> requestIds) {
        return jsonService.write(Map.of("requestIds", requestIds));
    }

    public String validatingSummary() {
        return jsonService.write(Map.of("validatedAt", OffsetDateTime.now().toString()));
    }

    public String finalizedSummary() {
        return jsonService.write(Map.of("finalizedAt", OffsetDateTime.now().toString()));
    }

    public String failureSummary(int attemptCount, String errorMessage, String reasonCode) {
        return jsonService.write(Map.of(
                "attemptCount", attemptCount,
                "reasonCode", reasonCode == null ? "" : reasonCode,
                "lastError", errorMessage,
                "lastFailedAt", OffsetDateTime.now().toString()
        ));
    }

    public String deadLetterSummary(int attemptCount, String errorMessage, String reasonCode) {
        return jsonService.write(Map.of(
                "attemptCount", attemptCount,
                "reasonCode", reasonCode == null ? "" : reasonCode,
                "lastError", errorMessage,
                "deadLetteredAt", OffsetDateTime.now().toString()
        ));
    }

    public record SettlementBatchDraft(
            String sourceDeviceId,
            String idempotencyKey,
            SettlementBatchStatus status,
            int proofsCount,
            String summaryJson
    ) {}
}
