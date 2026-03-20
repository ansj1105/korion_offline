package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.AdminOperationsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics")
public class AdminMetricsController {

    private final AdminOperationsService adminOperationsService;

    public AdminMetricsController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping("/settlements/timeseries")
    public Object settlementTimeseries(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String networkScope
    ) {
        return adminOperationsService.getSettlementDashboardMetrics(hours, networkScope);
    }

    @GetMapping("/offline-pay/overview")
    public Object offlinePayOverview(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String networkScope
    ) {
        return adminOperationsService.getOfflinePayOverview(days, networkScope);
    }
}
