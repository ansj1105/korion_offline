package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.FoxCoinStoreProductPolicyPort;
import io.korion.offlinepay.application.port.StoreProductRepository;
import io.korion.offlinepay.domain.model.StoreProduct;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProductApplicationService {

    private final StoreProductRepository storeProductRepository;
    private final FoxCoinStoreProductPolicyPort foxCoinStoreProductPolicyPort;

    public StoreProductApplicationService(
            StoreProductRepository storeProductRepository,
            FoxCoinStoreProductPolicyPort foxCoinStoreProductPolicyPort
    ) {
        this.storeProductRepository = storeProductRepository;
        this.foxCoinStoreProductPolicyPort = foxCoinStoreProductPolicyPort;
    }

    @Transactional(readOnly = true)
    public List<StoreProduct> getProducts(long userId) {
        validateUserId(userId);
        return storeProductRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public StoreProduct getProduct(long userId, long productId) {
        validateUserId(userId);
        return storeProductRepository.findByUserIdAndId(userId, productId)
                .orElseThrow(() -> new IllegalArgumentException("store product not found: " + productId));
    }

    @Transactional
    public StoreProduct createProduct(CreateStoreProductCommand command) {
        validateUserId(command.userId());
        validateStock(command.stockCurrent(), command.stockTotal());
        validateProductLimit(command.userId());
        BigDecimal normalizedPrice = normalizePrice(command.price());
        return storeProductRepository.save(
                command.userId(),
                normalizeText(command.name()),
                normalizeText(command.description()),
                normalizeText(command.imageUrl()),
                normalizedPrice,
                command.stockCurrent(),
                command.stockTotal(),
                command.visible(),
                storeProductRepository.nextSortOrder(command.userId())
        );
    }

    @Transactional
    public StoreProduct updateProduct(UpdateStoreProductCommand command) {
        validateUserId(command.userId());
        validateStock(command.stockCurrent(), command.stockTotal());
        normalizePrice(command.price());
        getProduct(command.userId(), command.productId());
        return storeProductRepository.update(
                command.userId(),
                command.productId(),
                normalizeText(command.name()),
                normalizeText(command.description()),
                normalizeText(command.imageUrl()),
                normalizePrice(command.price()),
                command.stockCurrent(),
                command.stockTotal(),
                command.visible()
        );
    }

    @Transactional
    public void deleteProduct(long userId, long productId) {
        validateUserId(userId);
        getProduct(userId, productId);
        storeProductRepository.delete(userId, productId);
    }

    @Transactional
    public List<StoreProduct> reorderProducts(ReorderStoreProductsCommand command) {
        validateUserId(command.userId());
        List<StoreProduct> currentProducts = storeProductRepository.findByUserId(command.userId());
        Set<Long> seen = new HashSet<>();
        int sortOrder = 1;

        for (Long productId : command.orderedProductIds()) {
            if (productId == null || productId <= 0 || !seen.add(productId)) {
                continue;
            }
            boolean exists = currentProducts.stream().anyMatch(item -> item.id() == productId);
            if (!exists) {
                continue;
            }
            storeProductRepository.updateSortOrder(command.userId(), productId, sortOrder++);
        }

        for (StoreProduct product : currentProducts) {
            if (seen.contains(product.id())) {
                continue;
            }
            storeProductRepository.updateSortOrder(command.userId(), product.id(), sortOrder++);
        }

        return storeProductRepository.findByUserId(command.userId());
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private void validateStock(int stockCurrent, int stockTotal) {
        if (stockCurrent < 0) {
            throw new IllegalArgumentException("stockCurrent must be zero or positive");
        }
        if (stockTotal < 0) {
            throw new IllegalArgumentException("stockTotal must be zero or positive");
        }
        if (stockCurrent > stockTotal) {
            throw new IllegalArgumentException("stockCurrent must not exceed stockTotal");
        }
    }

    private void validateProductLimit(long userId) {
        FoxCoinStoreProductPolicyPort.StoreProductPolicy policy =
                foxCoinStoreProductPolicyPort.getStoreProductPolicy(userId);
        int limit = Math.max(0, policy.storeProductLimit());
        int currentCount = storeProductRepository.countByUserId(userId);
        if (currentCount >= limit) {
            throw new IllegalArgumentException(
                    "STORE_PRODUCT_LIMIT_EXCEEDED: level=" + policy.level()
                            + ", limit=" + limit
                            + ", currentCount=" + currentCount
            );
        }
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        return price.stripTrailingZeros();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record CreateStoreProductCommand(
            long userId,
            String name,
            String description,
            String imageUrl,
            BigDecimal price,
            int stockCurrent,
            int stockTotal,
            boolean visible
    ) {}

    public record UpdateStoreProductCommand(
            long userId,
            long productId,
            String name,
            String description,
            String imageUrl,
            BigDecimal price,
            int stockCurrent,
            int stockTotal,
            boolean visible
    ) {}

    public record ReorderStoreProductsCommand(
            long userId,
            List<Long> orderedProductIds
    ) {}
}
