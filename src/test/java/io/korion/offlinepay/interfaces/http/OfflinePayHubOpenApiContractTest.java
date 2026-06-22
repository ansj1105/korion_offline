package io.korion.offlinepay.interfaces.http;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OfflinePayHubOpenApiContractTest {

    @Test
    void offlinePayHubServerProjectionEndpointsAreDocumented() throws Exception {
        String api = Files.readString(Path.of("docs/api.md"));

        assertTrue(api.contains("/api/offline-pay/client-events/batch:"));
        assertTrue(api.contains("/api/offline-pay/hub/projection:"));
        assertTrue(api.contains("/api/offline-pay/hub/summary:"));
        assertTrue(api.contains("/api/offline-pay/reconcile/commands/poll:"));
        assertTrue(api.contains("/api/offline-pay/reconcile/commands/{commandId}/report:"));
        assertTrue(api.contains("ClientEventBatchRequest:"));
        assertTrue(api.contains("HubProjectionResponse:"));
        assertTrue(api.contains("HubSummaryResponse:"));
        assertTrue(api.contains("PollReconcileCommandRequest:"));
        assertTrue(api.contains("PollReconcileCommandResponse:"));
        assertTrue(api.contains("ReportReconcileCommandRequest:"));
        assertTrue(api.contains("ReportReconcileCommandResponse:"));
        assertTrue(api.contains("enum: [SENT, RECEIVED]"));
    }
}
