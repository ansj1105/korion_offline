package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record RecordClientTraceRequest(
        @NotBlank String traceId,
        String sessionId,
        String failureCode,
        String flow,
        String status,
        String stage,
        String entryAction,
        String messageType,
        String routePeerId,
        String localSagaStatus,
        String nativeError,
        String deviceModel,
        String appVersion,
        String platform,
        Long userId,
        String deviceId,
        String message,
        Map<String, Object> metadata,
        List<ClientTraceStep> steps,
        String createdAt
) {

    public record ClientTraceStep(
            String id,
            String at,
            String level,
            String message,
            Map<String, Object> metadata
    ) {}
}
