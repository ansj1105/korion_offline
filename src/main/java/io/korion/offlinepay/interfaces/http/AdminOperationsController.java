package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.AdminOperationsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ops")
public class AdminOperationsController {

    private final AdminOperationsService adminOperationsService;

    public AdminOperationsController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping("/dead-letters")
    public Object listDeadLetters(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String networkScope
    ) {
        return Map.of("items", adminOperationsService.listDeadLetterBatches(size, networkScope));
    }

    @GetMapping("/collateral-operations")
    public Object listCollateralOperations(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assetCode
    ) {
        return Map.of("items", adminOperationsService.listCollateralOperations(size, operationType, status, assetCode));
    }

    @GetMapping("/collateral-operations/overview")
    public Object collateralOperationOverview(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String assetCode
    ) {
        return adminOperationsService.getCollateralOperationOverview(size, assetCode);
    }

    @GetMapping("/offline-events")
    public Object listOfflineEvents(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String eventStatus,
            @RequestParam(required = false) String assetCode
    ) {
        return Map.of("items", adminOperationsService.listOfflineEvents(size, eventType, eventStatus, assetCode));
    }

    @GetMapping("/offline-events/overview")
    public Object offlineEventOverview(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String assetCode
    ) {
        return adminOperationsService.getOfflineEventOverview(size, assetCode);
    }

    @PostMapping("/dead-letters/{batchId}/retry")
    public Object retryDeadLetter(@PathVariable String batchId) {
        return Map.of(
                "status", "REQUEUED",
                "batch", adminOperationsService.retryDeadLetterBatch(batchId)
        );
    }
}
