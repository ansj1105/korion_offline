package io.korion.offlinepay.interfaces.http.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.service.AdminOperationsService;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.application.service.SettlementApplicationService;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.OfflineSaga;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SettlementResponseFactoryTest {

    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final SettlementResponseFactory factory = new SettlementResponseFactory(jsonService);

    @Test
    void batchDetailIncludesCanonicalRequestIds() {
        SettlementBatch batch = new SettlementBatch(
                "batch-1",
                "device-1",
                "settlement_stl_local_abc",
                SettlementBatchStatus.SETTLED,
                null,
                1,
                jsonService.write(java.util.Map.of(
                        "triggerMode", "AUTO",
                        "requestIds", java.util.List.of("server-request-1")
                )),
                OffsetDateTime.parse("2026-04-07T00:00:00Z"),
                OffsetDateTime.parse("2026-04-07T00:05:00Z")
        );

        var response = factory.toBatchDetail(batch);

        assertEquals("batch-1", response.batchId());
        assertEquals("SETTLED", response.status());
        assertEquals("AUTO", response.triggerMode());
        assertEquals(java.util.List.of("server-request-1"), response.requestIds());
        assertEquals("settlement_stl_local_abc", response.idempotencyKey());
        assertEquals(1, response.acceptedCount());
        assertTrue(response.asyncProcessing());
        assertEquals("SERVER_ACCEPTED", response.serverWorkflowStage());
        assertEquals("LEDGER_SYNCED", response.settlementWorkflowStage());
    }

    @Test
    void batchDetailExposesQrOfflineAsyncAcceptanceContract() {
        SettlementBatch batch = new SettlementBatch(
                "batch-qr-1",
                "sender-device",
                "qr_offline_idempotency_1",
                SettlementBatchStatus.UPLOADED,
                null,
                1,
                jsonService.write(java.util.Map.of(
                        "triggerMode", "QR_OFFLINE_SYNC",
                        "requestIds", java.util.List.of("settlement-qr-1")
                )),
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z")
        );

        var response = factory.toBatchDetail(batch);

        assertEquals("batch-qr-1", response.batchId());
        assertEquals("UPLOADED", response.status());
        assertEquals("QR_OFFLINE_SYNC", response.triggerMode());
        assertEquals("qr_offline_idempotency_1", response.idempotencyKey());
        assertEquals(java.util.List.of("settlement-qr-1"), response.requestIds());
        assertEquals(1, response.acceptedCount());
        assertTrue(response.asyncProcessing());
        assertEquals("SERVER_ACCEPTED", response.serverWorkflowStage());
        assertEquals("SETTLEMENT_ACCEPTED", response.settlementWorkflowStage());
    }

    @Test
    void batchDetailMapsExternalSyncRetryableFailureFromRequestSaga() {
        SettlementBatch batch = new SettlementBatch(
                "batch-qr-1",
                "sender-device",
                "qr_offline_idempotency_1",
                SettlementBatchStatus.SETTLED,
                "SETTLED",
                1,
                jsonService.write(java.util.Map.of(
                        "triggerMode", "QR_OFFLINE_SYNC",
                        "requestIds", java.util.List.of("settlement-qr-1")
                )),
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z")
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-qr-1",
                "batch-qr-1",
                "collateral-1",
                "proof-1",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z")
        );
        OfflineSaga saga = new OfflineSaga(
                "saga-1",
                OfflineSagaType.SETTLEMENT,
                "settlement-qr-1",
                OfflineSagaStatus.COMPENSATION_REQUIRED,
                "COMPENSATION_REQUIRED",
                "HISTORY_SYNC_FAIL",
                "{}",
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z")
        );
        ReconciliationCase reconciliationCase = new ReconciliationCase(
                "case-1",
                "settlement-qr-1",
                "batch-qr-1",
                "proof-1",
                "voucher-1",
                "HISTORY_SYNC_FAILED",
                ReconciliationCaseStatus.OPEN,
                "HISTORY_SYNC_FAIL",
                jsonService.write(java.util.Map.of("retryable", true)),
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z"),
                null
        );

        var response = factory.toBatchDetail(new SettlementApplicationService.SettlementBatchDetailView(
                batch,
                java.util.List.of(new SettlementApplicationService.SettlementDetailView(
                        request,
                        saga,
                        reconciliationCase,
                        null,
                        null
                ))
        ));

        assertEquals("SERVER_ACCEPTED", response.serverWorkflowStage());
        assertEquals("RETRYABLE_FAILED", response.settlementWorkflowStage());
    }

    @Test
    void batchDetailMapsDeadLetteredFromRequestSaga() {
        SettlementBatch batch = new SettlementBatch(
                "batch-qr-1",
                "sender-device",
                "qr_offline_idempotency_1",
                SettlementBatchStatus.SETTLED,
                "SETTLED",
                1,
                jsonService.write(java.util.Map.of(
                        "triggerMode", "QR_OFFLINE_SYNC",
                        "requestIds", java.util.List.of("settlement-qr-1")
                )),
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z")
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-qr-1",
                "batch-qr-1",
                "collateral-1",
                "proof-1",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z")
        );
        OfflineSaga saga = new OfflineSaga(
                "saga-1",
                OfflineSagaType.SETTLEMENT,
                "settlement-qr-1",
                OfflineSagaStatus.DEAD_LETTERED,
                "DEAD_LETTERED",
                "LEDGER_SYNC_FAIL",
                "{}",
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z")
        );

        var response = factory.toBatchDetail(new SettlementApplicationService.SettlementBatchDetailView(
                batch,
                java.util.List.of(new SettlementApplicationService.SettlementDetailView(
                        request,
                        saga,
                        null,
                        null,
                        null
                ))
        ));

        assertEquals("DEAD_LETTERED", response.settlementWorkflowStage());
    }

    @Test
    void batchDetailMapsNonRetryableValidationFailureAsDeadLettered() {
        SettlementBatch batch = new SettlementBatch(
                "batch-qr-1",
                "sender-device",
                "qr_offline_idempotency_1",
                SettlementBatchStatus.FAILED,
                "INVALID_DEVICE_SIGNATURE",
                1,
                jsonService.write(java.util.Map.of(
                        "triggerMode", "QR_OFFLINE_SYNC",
                        "requestIds", java.util.List.of("settlement-qr-1")
                )),
                OffsetDateTime.parse("2026-06-06T00:00:00Z"),
                OffsetDateTime.parse("2026-06-06T00:00:01Z")
        );

        var response = factory.toBatchDetail(batch);

        assertEquals("DEAD_LETTERED", response.settlementWorkflowStage());
    }

    @Test
    void requestDetailIncludesReceiverConfirmationExpiryTracking() {
        OffsetDateTime deadlineAt = OffsetDateTime.parse("2026-06-08T01:00:00Z");
        SettlementRequest request = new SettlementRequest(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                deadlineAt,
                null,
                OffsetDateTime.parse("2026-06-08T00:00:00Z"),
                OffsetDateTime.parse("2026-06-08T00:10:00Z")
        );
        OfflineSaga saga = new OfflineSaga(
                "saga-1",
                OfflineSagaType.SETTLEMENT,
                "settlement-1",
                OfflineSagaStatus.PARTIALLY_APPLIED,
                "HISTORY_SYNCED",
                null,
                "{}",
                OffsetDateTime.parse("2026-06-08T00:00:00Z"),
                OffsetDateTime.parse("2026-06-08T00:10:00Z")
        );

        var response = factory.toDetailResponse(new SettlementApplicationService.SettlementDetailView(
                request,
                saga,
                null,
                null,
                null
        ));

        assertEquals("2026-06-08T01:00Z", response.receiverConfirmationDeadlineAt());
        assertEquals(null, response.receiverConfirmationExpiredAt());
        assertFalse(response.receiverConfirmationExpired());
    }

    @Test
    void requestDetailIncludesReceiverConfirmationExpiredAt() {
        OffsetDateTime deadlineAt = OffsetDateTime.parse("2026-06-08T01:00:00Z");
        OffsetDateTime expiredAt = OffsetDateTime.parse("2026-06-08T01:05:00Z");
        SettlementRequest request = new SettlementRequest(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                SettlementStatus.SETTLED,
                "RECEIVER_CONFIRMATION_EXPIRED",
                false,
                "{}",
                deadlineAt,
                expiredAt,
                OffsetDateTime.parse("2026-06-08T00:00:00Z"),
                OffsetDateTime.parse("2026-06-08T01:05:00Z")
        );
        OfflineSaga saga = new OfflineSaga(
                "saga-1",
                OfflineSagaType.SETTLEMENT,
                "settlement-1",
                OfflineSagaStatus.COMPENSATED,
                "COMPENSATED",
                "RECEIVER_CONFIRMATION_EXPIRED",
                "{}",
                OffsetDateTime.parse("2026-06-08T00:00:00Z"),
                OffsetDateTime.parse("2026-06-08T01:05:00Z")
        );

        var response = factory.toDetailResponse(new SettlementApplicationService.SettlementDetailView(
                request,
                saga,
                null,
                null,
                null
        ));

        assertEquals("2026-06-08T01:00Z", response.receiverConfirmationDeadlineAt());
        assertEquals("2026-06-08T01:05Z", response.receiverConfirmationExpiredAt());
        assertTrue(response.receiverConfirmationExpired());
    }

    @Test
    void reconciliationAdminResponseIncludesSagaLedgerAndRetryContext() {
        ReconciliationCase reconciliationCase = new ReconciliationCase(
                "case-1",
                "settlement-1",
                "batch-1",
                "proof-1",
                null,
                "HISTORY_SYNC_FAILURE",
                ReconciliationCaseStatus.OPEN,
                "HISTORY_SYNC_FAILED",
                jsonService.write(java.util.Map.of(
                        "retryable", true,
                        "nextAction", "RETRY_EXTERNAL_SYNC",
                        "eventType", "HISTORY_SYNC_REQUESTED",
                        "errorMessage", "history sync timeout"
                )),
                OffsetDateTime.parse("2026-04-07T00:00:00Z"),
                OffsetDateTime.parse("2026-04-07T00:05:00Z"),
                null
        );
        OfflineSaga settlementSaga = new OfflineSaga(
                "saga-1",
                OfflineSagaType.SETTLEMENT,
                "settlement-1",
                OfflineSagaStatus.COMPENSATION_REQUIRED,
                "LEDGER_COMPENSATION_REQUESTED",
                "HISTORY_SYNC_FAILED",
                jsonService.write(java.util.Map.of(
                        "ledgerResult", java.util.Map.of(
                                "ledgerOutcome", "COMPENSATED",
                                "accountingSide", "SENDER",
                                "receiverSettlementMode", "EXTERNAL_HISTORY_SYNC",
                                "settlementModel", "SENDER_LEDGER_PLUS_RECEIVER_HISTORY",
                                "reconciliationTrackingOwner", "OFFLINE_PAY_SAGA",
                                "duplicated", false,
                                "postAvailableBalance", "1020.923524",
                                "postLockedBalance", "0.000000",
                                "postOfflinePayPendingBalance", "12.500000"
                        )
                )),
                OffsetDateTime.parse("2026-04-07T00:00:00Z"),
                OffsetDateTime.parse("2026-04-07T00:05:00Z")
        );

        var response = factory.toReconciliationCaseAdminResponse(
                new AdminOperationsService.ReconciliationCaseView(reconciliationCase, settlementSaga)
        );

        assertEquals("case-1", response.caseId());
        assertEquals("COMPENSATION_REQUIRED", response.sagaStatus());
        assertEquals("LEDGER_COMPENSATION_REQUESTED", response.sagaStep());
        assertEquals("COMPENSATED", response.ledgerOutcome());
        assertEquals("SENDER", response.accountingSide());
        assertEquals("EXTERNAL_HISTORY_SYNC", response.receiverSettlementMode());
        assertEquals("SENDER_LEDGER_PLUS_RECEIVER_HISTORY", response.settlementModel());
        assertEquals("OFFLINE_PAY_SAGA", response.reconciliationTrackingOwner());
        assertEquals("1020.923524", response.postAvailableBalance().toPlainString());
        assertTrue(response.retryable());
        assertEquals("RETRY_EXTERNAL_SYNC", response.nextAction());
        assertEquals("HISTORY_SYNC_REQUESTED", response.eventType());
        assertEquals("history sync timeout", response.errorMessage());
    }
}
