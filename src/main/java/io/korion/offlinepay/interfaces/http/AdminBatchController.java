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
@RequestMapping("/api/admin/batches")
public class AdminBatchController {

    private final AdminOperationsService adminOperationsService;

    public AdminBatchController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping
    public Object listRecent(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String networkScope
    ) {
        return Map.of("items", adminOperationsService.listRecentBatches(size, networkScope));
    }

    @GetMapping("/overview")
    public Object overview(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String networkScope
    ) {
        return adminOperationsService.getBatchOverview(days, networkScope);
    }

    @GetMapping("/dead-letters")
    public Object listDeadLetters(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String networkScope
    ) {
        return Map.of("items", adminOperationsService.listDeadLetterBatches(size, networkScope));
    }

    @PostMapping("/{batchId}/retry")
    public Object retryDeadLetter(@PathVariable String batchId) {
        return Map.of(
                "status", "REQUEUED",
                "batch", adminOperationsService.retryDeadLetterBatch(batchId)
        );
    }
}
