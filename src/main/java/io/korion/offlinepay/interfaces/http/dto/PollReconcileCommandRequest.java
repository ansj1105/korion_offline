package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record PollReconcileCommandRequest(
        @NotBlank(message = "deviceId is required")
        String deviceId,
        String assetCode,
        Map<String, Object> localSummary
) {}
