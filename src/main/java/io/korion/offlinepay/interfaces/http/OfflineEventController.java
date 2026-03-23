package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.OfflineEventLogService;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import io.korion.offlinepay.interfaces.http.dto.RecordOfflineEventRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offline-events")
public class OfflineEventController {

    private final OfflineEventLogService offlineEventLogService;

    public OfflineEventController(OfflineEventLogService offlineEventLogService) {
        this.offlineEventLogService = offlineEventLogService;
    }

    @PostMapping
    public Object record(@Valid @RequestBody RecordOfflineEventRequest request) {
        return offlineEventLogService.record(new OfflineEventLogService.RecordOfflineEventCommand(
                request.userId(),
                request.deviceId(),
                OfflineEventType.valueOf(request.eventType().trim().toUpperCase()),
                OfflineEventStatus.valueOf(request.eventStatus().trim().toUpperCase()),
                request.assetCode(),
                request.networkCode(),
                request.amount(),
                request.requestId(),
                request.settlementId(),
                request.counterpartyDeviceId(),
                request.counterpartyActor(),
                request.reasonCode(),
                request.message(),
                request.metadata()
        ));
    }
}
