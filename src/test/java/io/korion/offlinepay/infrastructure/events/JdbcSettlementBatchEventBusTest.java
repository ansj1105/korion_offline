package io.korion.offlinepay.infrastructure.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.korion.offlinepay.domain.status.OfflineWorkflowEventType;
import io.korion.offlinepay.domain.status.OfflineWorkflowStage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JdbcSettlementBatchEventBusTest {

    private final JdbcSettlementBatchEventBus eventBus = new JdbcSettlementBatchEventBus(null, null, null);

    @Test
    void normalizeExternalSyncEventTypeAcceptsAllWorkerExternalSyncEvents() {
        for (OfflineWorkflowEventType eventType : new OfflineWorkflowEventType[] {
                OfflineWorkflowEventType.LEDGER_SYNC_REQUESTED,
                OfflineWorkflowEventType.HISTORY_SYNC_REQUESTED,
                OfflineWorkflowEventType.RECEIVER_HISTORY_SYNC_REQUESTED,
                OfflineWorkflowEventType.LEDGER_COMPENSATION_REQUESTED
        }) {
            assertEquals(
                    eventType.name(),
                    ReflectionTestUtils.invokeMethod(eventBus, "normalizeExternalSyncEventType", eventType.name())
            );
        }
    }

    @Test
    void resolveBatchResultStageKeepsSettledBatchesOutOfFailedStage() {
        assertEquals(
                OfflineWorkflowStage.LEDGER_SYNCED.name(),
                ReflectionTestUtils.invokeMethod(eventBus, "resolveBatchResultStage", "SETTLED", 1, 0)
        );
    }

    @Test
    void resolveBatchResultStageKeepsPartialSettlementsInFailedStage() {
        assertEquals(
                OfflineWorkflowStage.FAILED.name(),
                ReflectionTestUtils.invokeMethod(eventBus, "resolveBatchResultStage", "PARTIALLY_SETTLED", 1, 1)
        );
    }
}
