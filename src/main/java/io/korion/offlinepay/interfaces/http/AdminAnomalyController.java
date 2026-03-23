package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.AdminOperationsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/anomalies")
public class AdminAnomalyController {

    private final AdminOperationsService adminOperationsService;

    public AdminAnomalyController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping("/conflicts")
    public Object listConflicts(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String conflictType,
            @RequestParam(required = false) String collateralId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String networkScope
    ) {
        return Map.of(
                "items", adminOperationsService.listConflicts(status, conflictType, collateralId, deviceId, networkScope, size),
                "count", adminOperationsService.listConflicts(status, conflictType, collateralId, deviceId, networkScope, size).size()
        );
    }

    @GetMapping("/reconciliation-cases")
    public Object listReconciliationCases(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String reasonCode
    ) {
        return Map.of(
                "items", adminOperationsService.listReconciliationCases(size, status, caseType, reasonCode)
        );
    }
}
