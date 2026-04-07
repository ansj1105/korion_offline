package io.korion.offlinepay.interfaces.http.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.service.AdminOperationsService;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.OfflineSaga;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SettlementResponseFactoryTest {

    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final SettlementResponseFactory factory = new SettlementResponseFactory(jsonService);

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
