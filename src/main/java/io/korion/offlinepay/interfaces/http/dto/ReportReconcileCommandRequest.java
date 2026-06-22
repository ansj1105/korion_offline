package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ReportReconcileCommandRequest(
        @NotBlank(message = "deviceId is required")
        String deviceId,
        @NotBlank(message = "nonce is required")
        String nonce,
        @NotBlank(message = "status is required")
        String status,
        Map<String, Object> dryRunSummary,
        Map<String, Object> applySummary,
        Map<String, Object> localSummary,
        String errorMessage
) {}
