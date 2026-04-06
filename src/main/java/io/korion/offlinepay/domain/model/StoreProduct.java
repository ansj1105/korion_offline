package io.korion.offlinepay.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StoreProduct(
        long id,
        long userId,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        int stockCurrent,
        int stockTotal,
        boolean visible,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
