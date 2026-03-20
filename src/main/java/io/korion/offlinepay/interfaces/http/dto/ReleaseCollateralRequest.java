package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ReleaseCollateralRequest(
        @NotNull @Min(1) Long userId,
        @NotBlank String deviceId,
        String reason,
        Map<String, Object> metadata
) {}
