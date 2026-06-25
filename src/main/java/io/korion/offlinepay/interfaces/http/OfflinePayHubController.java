package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.ClientEventBatchService;
import io.korion.offlinepay.application.service.OfflineLedgerService;
import io.korion.offlinepay.interfaces.http.dto.SubmitClientEventBatchRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offline-pay")
public class OfflinePayHubController {

    private final ClientEventBatchService clientEventBatchService;
    private final OfflineLedgerService offlineLedgerService;

    public OfflinePayHubController(
            ClientEventBatchService clientEventBatchService,
            OfflineLedgerService offlineLedgerService
    ) {
        this.clientEventBatchService = clientEventBatchService;
        this.offlineLedgerService = offlineLedgerService;
    }

    @PostMapping("/client-events/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ClientEventBatchService.ClientEventBatchResponse submitClientEvents(
            @Valid @RequestBody SubmitClientEventBatchRequest request
    ) {
        return clientEventBatchService.submit(request);
    }

    @GetMapping("/hub/projection")
    public OfflineLedgerService.HubProjectionResponse getHubProjection(
            @RequestParam String deviceId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "SENT") String tab,
            @RequestParam(required = false) String assetCode,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer page
    ) {
        return offlineLedgerService.getHubProjection(deviceId, userId, tab, assetCode, limit, page);
    }

    @GetMapping("/hub/summary")
    public OfflineLedgerService.HubSummaryResponse getHubSummary(
            @RequestParam String deviceId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String assetCode
    ) {
        return offlineLedgerService.getHubSummary(deviceId, userId, assetCode);
    }
}
