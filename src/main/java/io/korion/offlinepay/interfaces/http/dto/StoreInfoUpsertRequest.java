package io.korion.offlinepay.interfaces.http.dto;

import io.korion.offlinepay.domain.model.StoreInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StoreInfoUpsertRequest(
        @NotNull @Min(1) Long userId,
        @NotBlank String storeName,
        String description,
        String address,
        String contactPhone,
        @Valid StoreInfo.BusinessHours businessHours,
        String category,
        String logoImageUrl,
        String backgroundImageUrl
) {}
