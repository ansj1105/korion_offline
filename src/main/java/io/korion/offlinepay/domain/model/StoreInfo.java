package io.korion.offlinepay.domain.model;

import java.time.OffsetDateTime;

public record StoreInfo(
        long userId,
        String storeName,
        String description,
        String address,
        String contactPhone,
        BusinessHours businessHours,
        String category,
        String logoImageUrl,
        String backgroundImageUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record BusinessHours(
            String weekday,
            String weekend,
            String holiday
    ) {}
}
