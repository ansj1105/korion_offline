package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

public record CreateCollateralRequest(
        @NotNull @Min(1) Long userId,
        @NotBlank String deviceId,
        @NotNull @DecimalMin("0.00000001") BigDecimal amount,
        String assetCode,
        String initialStateRoot,
        @Min(1) Integer policyVersion,
        Map<String, Object> metadata
) {}

