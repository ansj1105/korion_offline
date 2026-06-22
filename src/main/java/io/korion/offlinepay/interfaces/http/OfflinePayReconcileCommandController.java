package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.OfflinePayReconcileCommandService;
import io.korion.offlinepay.interfaces.http.dto.PollReconcileCommandRequest;
import io.korion.offlinepay.interfaces.http.dto.ReportReconcileCommandRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offline-pay/reconcile")
public class OfflinePayReconcileCommandController {

    private final OfflinePayReconcileCommandService service;

    public OfflinePayReconcileCommandController(OfflinePayReconcileCommandService service) {
        this.service = service;
    }

    @PostMapping("/commands/poll")
    public OfflinePayReconcileCommandService.PollResponse poll(@Valid @RequestBody PollReconcileCommandRequest request) {
        return service.poll(new OfflinePayReconcileCommandService.PollCommand(
                request.deviceId(),
                request.assetCode(),
                request.localSummary()
        ));
    }

    @PostMapping("/commands/{commandId}/report")
    public OfflinePayReconcileCommandService.ReportResponse report(
            @PathVariable String commandId,
            @Valid @RequestBody ReportReconcileCommandRequest request
    ) {
        return service.report(new OfflinePayReconcileCommandService.ReportCommand(
                request.deviceId(),
                commandId,
                request.nonce(),
                request.status(),
                request.dryRunSummary() == null ? java.util.Map.of() : request.dryRunSummary(),
                request.applySummary() == null ? java.util.Map.of() : request.applySummary(),
                request.localSummary() == null ? java.util.Map.of() : request.localSummary(),
                request.errorMessage()
        ));
    }
}
