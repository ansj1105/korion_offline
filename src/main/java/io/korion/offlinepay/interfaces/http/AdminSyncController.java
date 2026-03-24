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
@RequestMapping("/api/admin/sync")
public class AdminSyncController {

    private final AdminOperationsService adminOperationsService;

    public AdminSyncController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
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

    @GetMapping("/outbox")
    public Object listOutbox(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status
    ) {
        return Map.of("items", adminOperationsService.listOutboxEvents(size, eventType, status));
    }

    @GetMapping("/outbox/overview")
    public Object outboxOverview() {
        return adminOperationsService.getOutboxOverview();
    }

    @GetMapping("/batches/dead-letters")
    public Object listDeadLetters(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String networkScope
    ) {
        return Map.of("items", adminOperationsService.listDeadLetterBatches(size, networkScope));
    }

    @PostMapping("/batches/{batchId}/retry")
    public Object retryDeadLetter(@PathVariable String batchId) {
        return Map.of(
                "status", "REQUEUED",
                "batch", adminOperationsService.retryDeadLetterBatch(batchId)
        );
    }
}
