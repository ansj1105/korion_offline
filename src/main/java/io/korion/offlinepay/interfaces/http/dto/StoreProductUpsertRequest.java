package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StoreProductUpsertRequest(
        @NotNull @Min(1) Long userId,
        @NotBlank String name,
        String description,
        String imageUrl,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal price,
        @NotNull @Min(0) Integer stockCurrent,
        @NotNull @Min(0) Integer stockTotal,
        @NotNull Boolean visible
) {}
