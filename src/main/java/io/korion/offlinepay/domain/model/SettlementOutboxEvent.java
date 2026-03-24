package io.korion.offlinepay.domain.model;

import java.time.OffsetDateTime;

public record SettlementOutboxEvent(
        String id,
        String eventType,
        String status,
        String batchId,
        String uploaderType,
        String uploaderDeviceId,
        String payloadJson,
        int attempts,
        String lockOwner,
        OffsetDateTime lockedAt,
        OffsetDateTime processedAt,
        OffsetDateTime deadLetteredAt,
        String reasonCode,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public String getWorkflowStage() {
        return extractPayloadValue("workflowStage");
    }

    private String extractPayloadValue(String fieldName) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return "";
        }
        String token = "\"" + fieldName + "\":\"";
        int start = payloadJson.indexOf(token);
        if (start < 0) {
            return "";
        }
        int valueStart = start + token.length();
        int valueEnd = payloadJson.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return "";
        }
        return payloadJson.substring(valueStart, valueEnd);
    }
}
