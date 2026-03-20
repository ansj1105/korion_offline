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

    @PostMapping("/dead-letters/{batchId}/retry")
    public Object retryDeadLetter(@PathVariable String batchId) {
        return Map.of(
                "status", "REQUEUED",
                "batch", adminOperationsService.retryDeadLetterBatch(batchId)
        );
    }
}
