package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StoreProductReorderRequest(
        @NotNull @Min(1) Long userId,
        @NotEmpty List<@NotNull @Min(1) Long> orderedProductIds
) {}
