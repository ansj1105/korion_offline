package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.AdminOperationsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/proofs")
public class AdminProofController {

    private final AdminOperationsService adminOperationsService;

    public AdminProofController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping
    public Object listProofs(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channelType
    ) {
        return Map.of("items", adminOperationsService.listProofs(size, status, channelType));
    }

    @GetMapping("/overview")
    public Object overview(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String channelType
    ) {
        return adminOperationsService.getProofOverview(size, channelType);
    }
}
