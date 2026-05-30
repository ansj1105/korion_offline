package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.StoreInfoApplicationService;
import io.korion.offlinepay.domain.model.StoreInfo;
import io.korion.offlinepay.interfaces.http.dto.StoreInfoUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/info")
public class StoreInfoController {

    private final StoreInfoApplicationService storeInfoApplicationService;

    public StoreInfoController(StoreInfoApplicationService storeInfoApplicationService) {
        this.storeInfoApplicationService = storeInfoApplicationService;
    }

    @GetMapping
    public StoreInfo get(@RequestParam long userId) {
        return storeInfoApplicationService.getStoreInfo(userId)
                .orElseGet(() -> new StoreInfo(
                        userId,
                        "",
                        "",
                        "",
                        "",
                        new StoreInfo.BusinessHours("", "", ""),
                        "etc",
                        "",
                        "",
                        null,
                        null
                ));
    }

    @PutMapping
    public StoreInfo upsert(@Valid @RequestBody StoreInfoUpsertRequest request) {
        return storeInfoApplicationService.upsertStoreInfo(
                new StoreInfoApplicationService.UpsertStoreInfoCommand(
                        request.userId(),
                        request.storeName(),
                        request.description(),
                        request.address(),
                        request.contactPhone(),
                        request.businessHours(),
                        request.category(),
                        request.logoImageUrl(),
                        request.backgroundImageUrl()
                )
        );
    }
}
