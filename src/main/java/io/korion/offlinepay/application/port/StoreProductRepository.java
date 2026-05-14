package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.StoreProduct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StoreProductRepository {

    List<StoreProduct> findByUserId(long userId);

    Optional<StoreProduct> findByUserIdAndId(long userId, long productId);

    int countByUserId(long userId);

    int nextSortOrder(long userId);

    StoreProduct save(
            long userId,
            String name,
            String description,
            String imageUrl,
            BigDecimal price,
            int stockCurrent,
            int stockTotal,
            boolean visible,
            int sortOrder
    );

    StoreProduct update(
            long userId,
            long productId,
            String name,
            String description,
            String imageUrl,
            BigDecimal price,
            int stockCurrent,
            int stockTotal,
            boolean visible
    );

    void delete(long userId, long productId);

    void updateSortOrder(long userId, long productId, int sortOrder);
}
