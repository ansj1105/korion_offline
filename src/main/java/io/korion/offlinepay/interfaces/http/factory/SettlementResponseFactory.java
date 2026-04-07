package io.korion.offlinepay.interfaces.http.factory;

import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.application.service.SettlementApplicationService.SettlementDetailView;
import io.korion.offlinepay.interfaces.http.dto.FinalizeSettlementResponse;
import io.korion.offlinepay.interfaces.http.dto.ReconciliationCaseAdminResponse;
import io.korion.offlinepay.interfaces.http.dto.SettlementBatchDetailResponse;
import io.korion.offlinepay.interfaces.http.dto.SettlementRequestDetailResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
        return new SettlementBatchDetailResponse(batch.id(), batch.status().name(), batch.proofsCount(), triggerMode);
    }

    public FinalizeSettlementResponse toFinalizeResponse(SettlementRequest settlementRequest) {
        return new FinalizeSettlementResponse(settlementRequest.id(), settlementRequest.status().name());
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
                settlementRequest.status().name(),
                settlementRequest.reasonCode(),
                settlementRequest.conflictDetected(),
                settlementRequest.updatedAt() == null ? null : settlementRequest.updatedAt().toString(),
                settlementSaga == null || settlementSaga.status() == null ? null : settlementSaga.status().name(),
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
                boolOrNull(ledgerResult, "duplicated"),
                decimalOrNull(ledgerResult, "postAvailableBalance"),
                decimalOrNull(ledgerResult, "postLockedBalance"),
                decimalOrNull(ledgerResult, "postOfflinePayPendingBalance"),
                senderHistoryStatus,
                receiverHistoryStatus
        );
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
                boolOrNull(ledgerResult, "duplicated"),
                decimalOrNull(ledgerResult, "postAvailableBalance"),
                decimalOrNull(ledgerResult, "postLockedBalance"),
                decimalOrNull(ledgerResult, "postOfflinePayPendingBalance"),
                boolOrNull(detail, "retryable"),
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
