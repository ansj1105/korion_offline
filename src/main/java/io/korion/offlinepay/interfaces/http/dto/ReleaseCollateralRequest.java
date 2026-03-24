package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.Map;

public record ReleaseCollateralRequest(
        @NotNull @Min(1) Long userId,
        @NotBlank String deviceId,
        @NotNull @DecimalMin("0.00000001") BigDecimal amount,
        String reason,
        Map<String, Object> metadata
) {}
