package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.StoreProductApplicationService;
import io.korion.offlinepay.domain.model.StoreProduct;
import io.korion.offlinepay.interfaces.http.dto.StoreProductReorderRequest;
import io.korion.offlinepay.interfaces.http.dto.StoreProductUpsertRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/products")
public class StoreProductController {

    private final StoreProductApplicationService storeProductApplicationService;

    public StoreProductController(StoreProductApplicationService storeProductApplicationService) {
        this.storeProductApplicationService = storeProductApplicationService;
    }

    @GetMapping
    public List<StoreProduct> list(@RequestParam long userId) {
        return storeProductApplicationService.getProducts(userId);
    }

    @GetMapping("/{productId}")
    public StoreProduct detail(
            @PathVariable long productId,
            @RequestParam long userId
    ) {
        return storeProductApplicationService.getProduct(userId, productId);
    }

    @PostMapping
    public StoreProduct create(@Valid @RequestBody StoreProductUpsertRequest request) {
        return storeProductApplicationService.createProduct(
                new StoreProductApplicationService.CreateStoreProductCommand(
                        request.userId(),
                        request.name(),
                        request.description(),
                        request.imageUrl(),
                        request.price(),
                        request.stockCurrent(),
                        request.stockTotal(),
                        request.visible()
                )
        );
    }

    @PutMapping("/{productId}")
    public StoreProduct update(
            @PathVariable long productId,
            @Valid @RequestBody StoreProductUpsertRequest request
    ) {
        return storeProductApplicationService.updateProduct(
                new StoreProductApplicationService.UpdateStoreProductCommand(
                        request.userId(),
                        productId,
                        request.name(),
                        request.description(),
                        request.imageUrl(),
                        request.price(),
                        request.stockCurrent(),
                        request.stockTotal(),
                        request.visible()
                )
        );
    }

    @DeleteMapping("/{productId}")
    public void delete(
            @PathVariable long productId,
            @RequestParam long userId
    ) {
        storeProductApplicationService.deleteProduct(userId, productId);
    }

    @PutMapping("/reorder")
    public List<StoreProduct> reorder(@Valid @RequestBody StoreProductReorderRequest request) {
        return storeProductApplicationService.reorderProducts(
                new StoreProductApplicationService.ReorderStoreProductsCommand(
                        request.userId(),
                        request.orderedProductIds()
                )
        );
    }
}
