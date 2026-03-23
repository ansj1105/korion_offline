package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.AdminOperationsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/collateral")
public class AdminCollateralController {

    private final AdminOperationsService adminOperationsService;

    public AdminCollateralController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping("/operations")
    public Object listOperations(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assetCode
    ) {
        return Map.of(
                "items", adminOperationsService.listCollateralOperations(size, operationType, status, assetCode)
        );
    }

    @GetMapping("/overview")
    public Object overview(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String assetCode
    ) {
        return adminOperationsService.getCollateralOperationOverview(size, assetCode);
    }
}
