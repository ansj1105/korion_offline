package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.AdminOperationsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conflicts")
public class AdminConflictController {

    private final AdminOperationsService adminOperationsService;

    public AdminConflictController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String conflictType,
            @RequestParam(required = false) String collateralId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String networkScope,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return Map.of(
                "items", adminOperationsService.listConflicts(status, conflictType, collateralId, deviceId, networkScope, size),
                "nextCursor", cursor
        );
    }
}
