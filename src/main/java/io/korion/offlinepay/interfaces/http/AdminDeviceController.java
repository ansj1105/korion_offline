package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.AdminOperationsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/devices")
public class AdminDeviceController {

    private final AdminOperationsService adminOperationsService;

    public AdminDeviceController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping
    public Object listDevices(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        return Map.of("items", adminOperationsService.listDevices(size, status));
    }

    @GetMapping("/overview")
    public Object overview(@RequestParam(defaultValue = "20") int size) {
        return adminOperationsService.getDeviceOverview(size);
    }
}
