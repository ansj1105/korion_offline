package io.korion.offlinepay.interfaces.http.factory;

import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.application.service.SettlementApplicationService.SettlementBatchDetailView;
import io.korion.offlinepay.application.service.SettlementApplicationService.SettlementDetailView;
import io.korion.offlinepay.domain.policy.OfflineFailurePolicy;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import io.korion.offlinepay.interfaces.http.dto.FinalizeSettlementResponse;
import io.korion.offlinepay.interfaces.http.dto.ReconciliationCaseAdminResponse;
import io.korion.offlinepay.interfaces.http.dto.SettlementBatchDetailResponse;
import io.korion.offlinepay.interfaces.http.dto.SettlementRequestDetailResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SettlementResponseFactory {

    private final JsonService jsonService;

    public SettlementResponseFactory(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public SettlementBatchDetailResponse toBatchDetail(SettlementBatch batch) {
        JsonNode summary = jsonService.readTree(batch.summaryJson());
        String triggerMode = summary.path("triggerMode").isMissingNode() ? "MANUAL" : summary.path("triggerMode").asText("MANUAL");
        List<String> requestIds = requestIdsFrom(summary);
        return new SettlementBatchDetailResponse(
                batch.id(),
                batch.status().name(),
                batch.proofsCount(),
                triggerMode,
                requestIds,
                batch.idempotencyKey(),
                requestIds.size(),
                true,
                resolveServerWorkflowStage(batch),
                resolveSettlementWorkflowStage(batch, summary, requestIds)
        );
    }

    public SettlementBatchDetailResponse toBatchDetail(SettlementBatchDetailView detailView) {
        SettlementBatch batch = detailView.batch();
        JsonNode summary = jsonService.readTree(batch.summaryJson());
        String triggerMode = summary.path("triggerMode").isMissingNode() ? "MANUAL" : summary.path("triggerMode").asText("MANUAL");
        List<String> requestIds = requestIdsFrom(summary);
        if (requestIds.isEmpty()) {
            requestIds = detailView.settlements().stream()
                    .map(view -> view.settlementRequest().id())
                    .toList();
        }
        return new SettlementBatchDetailResponse(
                batch.id(),
                batch.status().name(),
                batch.proofsCount(),
                triggerMode,
                requestIds,
                batch.idempotencyKey(),
                requestIds.size(),
                true,
                resolveServerWorkflowStage(batch),
                resolveSettlementWorkflowStage(batch, summary, requestIds, detailView.settlements())
        );
    }

    private String resolveServerWorkflowStage(SettlementBatch batch) {
        return switch (batch.status()) {
            case CREATED -> "SERVER_ACCEPTING";
            case UPLOADED, VALIDATING, PARTIALLY_SETTLED, SETTLED, FAILED, CLOSED -> "SERVER_ACCEPTED";
        };
    }

    private String resolveSettlementWorkflowStage(SettlementBatch batch, JsonNode summary, List<String> requestIds) {
        if (requestIds.isEmpty()) {
            return null;
        }
        if (batch.status() == io.korion.offlinepay.domain.status.SettlementBatchStatus.FAILED
                && isNonRetryableFailure(batch.lastReasonCode(), summary)) {
            return "DEAD_LETTERED";
        }
        return switch (batch.status()) {
            case CREATED -> null;
            case UPLOADED, VALIDATING, PARTIALLY_SETTLED -> "SETTLEMENT_ACCEPTED";
            case SETTLED -> "LEDGER_SYNCED";
            case FAILED -> summary.path("deadLetteredAt").isMissingNode() ? "RETRYABLE_FAILED" : "DEAD_LETTERED";
            case CLOSED -> "DEAD_LETTERED";
        };
    }

    private String resolveSettlementWorkflowStage(
            SettlementBatch batch,
            JsonNode summary,
            List<String> requestIds,
            List<SettlementDetailView> settlements
    ) {
        if (requestIds.isEmpty()) {
            return null;
        }
        if (settlements == null || settlements.isEmpty()) {
            return resolveSettlementWorkflowStage(batch, summary, requestIds);
        }
        boolean hasDeadLettered = false;
        boolean hasRetryableFailure = false;
        boolean hasExternalSyncInProgress = false;
        boolean allCompleted = true;

        for (SettlementDetailView settlement : settlements) {
            var saga = settlement.settlementSaga();
            var reconciliationCase = settlement.reconciliationCase();
            if (saga == null) {
                allCompleted = false;
                continue;
            }
            if (saga.status() != OfflineSagaStatus.COMPLETED
                    && saga.status() != OfflineSagaStatus.COMPENSATED) {
                allCompleted = false;
            }
            if (saga.status() == OfflineSagaStatus.DEAD_LETTERED) {
                hasDeadLettered = true;
            } else if (saga.status() == OfflineSagaStatus.FAILED && isNonRetryableFailure(saga.lastReasonCode(), null)) {
                hasDeadLettered = true;
            } else if (saga.status() == OfflineSagaStatus.FAILED
                    || saga.status() == OfflineSagaStatus.COMPENSATION_REQUIRED
                    || saga.status() == OfflineSagaStatus.COMPENSATING) {
                hasRetryableFailure = true;
            }
            if (reconciliationCase != null) {
                JsonNode detail = jsonService.readTree(reconciliationCase.detailJson());
                if (boolOrNull(detail, "retryable") == Boolean.TRUE) {
                    hasRetryableFailure = true;
                } else {
                    hasDeadLettered = true;
                }
            }
            String currentStep = saga.currentStep();
            if ("EXTERNAL_SYNC_REQUESTED".equals(currentStep)
                    || "LEDGER_SYNCED".equals(currentStep)
                    || "HISTORY_SYNCED".equals(currentStep)
                    || "RECEIVER_HISTORY_SYNCED".equals(currentStep)) {
                hasExternalSyncInProgress = true;
            }
        }

        if (hasDeadLettered) {
            return "DEAD_LETTERED";
        }
        if (hasRetryableFailure) {
            return "RETRYABLE_FAILED";
        }
        if (allCompleted || hasExternalSyncInProgress) {
            return "LEDGER_SYNCED";
        }
        return resolveSettlementWorkflowStage(batch, summary, requestIds);
    }

    private boolean isNonRetryableFailure(String reasonCode, JsonNode summary) {
        String errorMessage = summary == null ? null : textOrNull(summary, "errorMessage");
        return !OfflineFailurePolicy.isRetryable(OfflineFailurePolicy.classify(reasonCode, errorMessage));
    }

    private List<String> requestIdsFrom(JsonNode summary) {
        JsonNode requestIds = summary.path("requestIds");
        if (!requestIds.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        requestIds.forEach(requestId -> {
            if (requestId != null && requestId.isTextual() && !requestId.asText().isBlank()) {
                values.add(requestId.asText());
            }
        });
        return values;
    }

    public FinalizeSettlementResponse toFinalizeResponse(SettlementRequest settlementRequest) {
        return new FinalizeSettlementResponse(settlementRequest.id(), normalizePublicSettlementStatus(settlementRequest.status()));
    }

    public SettlementRequestDetailResponse toDetailResponse(SettlementDetailView detailView) {
        SettlementRequest settlementRequest = detailView.settlementRequest();
        var settlementSaga = detailView.settlementSaga();
        var reconciliationCase = detailView.reconciliationCase();
        OfflinePaymentProof proof = detailView.proof();
        CollateralLock collateral = detailView.collateral();
        JsonNode ledgerResult = settlementSaga == null ? null : jsonService.readTree(settlementSaga.payloadJson()).path("ledgerResult");
        String sagaCurrentStep = settlementSaga == null ? null : settlementSaga.currentStep();
        String senderHistoryStatus = resolveSenderHistoryStatus(sagaCurrentStep);
        String receiverHistoryStatus = resolveReceiverHistoryStatus(sagaCurrentStep, proof);
        return new SettlementRequestDetailResponse(
                settlementRequest.id(),
                settlementRequest.batchId(),
                normalizePublicSettlementStatus(settlementRequest.status()),
                settlementRequest.reasonCode(),
                settlementRequest.conflictDetected(),
                settlementRequest.updatedAt() == null ? null : settlementRequest.updatedAt().toString(),
                settlementSaga == null || settlementSaga.status() == null ? null : normalizePublicSagaStatus(settlementSaga.status()),
                settlementSaga == null ? null : settlementSaga.currentStep(),
                settlementSaga == null ? null : settlementSaga.recoveryMode(),
                settlementSaga == null ? null : settlementSaga.lastReasonCode(),
                reconciliationCase == null ? null : reconciliationCase.caseType(),
                reconciliationCase == null || reconciliationCase.status() == null ? null : reconciliationCase.status().name(),
                reconciliationCase == null ? null : reconciliationCase.reasonCode(),
                proof == null ? null : proof.senderDeviceId(),
                proof == null ? null : proof.receiverDeviceId(),
                proof == null ? null : proof.amount(),
                proof == null ? null : proof.channelType(),
                collateral == null ? null : collateral.lockedAmount(),
                collateral == null ? null : collateral.remainingAmount(),
                textOrNull(ledgerResult, "ledgerOutcome"),
                textOrNull(ledgerResult, "accountingSide"),
                textOrNull(ledgerResult, "receiverSettlementMode"),
                textOrNull(ledgerResult, "settlementModel"),
                textOrNull(ledgerResult, "reconciliationTrackingOwner"),
                boolOrNull(ledgerResult, "duplicated"),
                decimalOrNull(ledgerResult, "postAvailableBalance"),
                decimalOrNull(ledgerResult, "postLockedBalance"),
                decimalOrNull(ledgerResult, "postOfflinePayPendingBalance"),
                settlementRequest.receiverConfirmationDeadlineAt() == null ? null : settlementRequest.receiverConfirmationDeadlineAt().toString(),
                settlementRequest.receiverConfirmationExpiredAt() == null ? null : settlementRequest.receiverConfirmationExpiredAt().toString(),
                settlementRequest.receiverConfirmationExpiredAt() == null ? Boolean.FALSE : Boolean.TRUE,
                senderHistoryStatus,
                receiverHistoryStatus
        );
    }

    private String normalizePublicSettlementStatus(SettlementStatus status) {
        if (status == null) {
            return "PENDING";
        }
        return switch (status) {
            case PENDING, VALIDATING -> "PENDING";
            case SETTLED -> "SETTLED";
            case CONFLICT, REJECTED, EXPIRED -> "FAILED";
        };
    }

    private String normalizePublicSagaStatus(OfflineSagaStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case COMPLETED -> "SETTLED";
            case COMPENSATED, FAILED, DEAD_LETTERED -> "FAILED";
            case QUEUED, ACCEPTED, PROCESSING, PARTIALLY_APPLIED, COMPENSATION_REQUIRED, COMPENSATING -> "PENDING";
        };
    }

    public ReconciliationCaseAdminResponse toReconciliationCaseAdminResponse(
            io.korion.offlinepay.application.service.AdminOperationsService.ReconciliationCaseView view
    ) {
        var reconciliationCase = view.reconciliationCase();
        var settlementSaga = view.settlementSaga();
        JsonNode detail = reconciliationCase == null ? null : jsonService.readTree(reconciliationCase.detailJson());
        JsonNode ledgerResult = settlementSaga == null ? null : jsonService.readTree(settlementSaga.payloadJson()).path("ledgerResult");
        return new ReconciliationCaseAdminResponse(
                reconciliationCase.id(),
                reconciliationCase.settlementId(),
                reconciliationCase.batchId(),
                reconciliationCase.proofId(),
                reconciliationCase.voucherId(),
                reconciliationCase.caseType(),
                reconciliationCase.status() == null ? null : reconciliationCase.status().name(),
                reconciliationCase.reasonCode(),
                reconciliationCase.createdAt() == null ? null : reconciliationCase.createdAt().toString(),
                reconciliationCase.updatedAt() == null ? null : reconciliationCase.updatedAt().toString(),
                reconciliationCase.resolvedAt() == null ? null : reconciliationCase.resolvedAt().toString(),
                settlementSaga == null || settlementSaga.status() == null ? null : settlementSaga.status().name(),
                settlementSaga == null ? null : settlementSaga.currentStep(),
                settlementSaga == null ? null : settlementSaga.recoveryMode(),
                settlementSaga == null ? null : settlementSaga.lastReasonCode(),
                textOrNull(ledgerResult, "ledgerOutcome"),
                textOrNull(ledgerResult, "accountingSide"),
                textOrNull(ledgerResult, "receiverSettlementMode"),
                textOrNull(ledgerResult, "settlementModel"),
                textOrNull(ledgerResult, "reconciliationTrackingOwner"),
                boolOrNull(ledgerResult, "duplicated"),
                decimalOrNull(ledgerResult, "postAvailableBalance"),
                decimalOrNull(ledgerResult, "postLockedBalance"),
                decimalOrNull(ledgerResult, "postOfflinePayPendingBalance"),
                boolOrNull(detail, "retryable"),
                textOrNull(detail, "adminAction"),
                textOrNull(detail, "manualOperatorId"),
                textOrNull(detail, "manualReason"),
                textOrNull(detail, "manualActionExecutedAt"),
                textOrNull(detail, "manualCompensationIdempotencyKey"),
                textOrNull(detail, "manualCompensationRequestedAt"),
                textOrNull(detail, "manualResolvedAt"),
                textOrNull(detail, "manualClosedAt"),
                textOrNull(detail, "nextAction"),
                textOrNull(detail, "eventType"),
                textOrNull(detail, "errorMessage"),
                textOrNull(detail, "lastManualRetryAt"),
                textOrNull(detail, "nextRetryAt")
        );
    }

    private String resolveSenderHistoryStatus(String sagaStep) {
        if (sagaStep == null) {
            return "PENDING";
        }
        return switch (sagaStep) {
            case "HISTORY_SYNCED", "RECEIVER_HISTORY_SYNCED" -> "SYNCED";
            default -> "PENDING";
        };
    }

    private String resolveReceiverHistoryStatus(String sagaStep, OfflinePaymentProof proof) {
        if (proof == null || proof.receiverDeviceId() == null || proof.receiverDeviceId().isBlank()) {
            return "N/A";
        }
        if ("RECEIVER_HISTORY_SYNCED".equals(sagaStep)) {
            return "SYNCED";
        }
        return "PENDING";
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).asText();
    }

    private Boolean boolOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).asBoolean();
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        String value = textOrNull(node, field);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
