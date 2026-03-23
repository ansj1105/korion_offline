package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.OfflineSnapshotService;
import io.korion.offlinepay.application.service.OfflineSnapshotStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/snapshots")
public class OfflineSnapshotController {

    private final OfflineSnapshotService offlineSnapshotService;
    private final OfflineSnapshotStreamService offlineSnapshotStreamService;

    public OfflineSnapshotController(
            OfflineSnapshotService offlineSnapshotService,
            OfflineSnapshotStreamService offlineSnapshotStreamService
    ) {
        this.offlineSnapshotService = offlineSnapshotService;
        this.offlineSnapshotStreamService = offlineSnapshotStreamService;
    }

    @GetMapping("/current")
    public OfflineSnapshotService.CurrentSnapshot current(
            @RequestParam long userId,
            @RequestParam String deviceId,
            @RequestParam(required = false) String assetCode
    ) {
        return offlineSnapshotService.getCurrentSnapshot(userId, deviceId, assetCode);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam long userId,
            @RequestParam String deviceId
    ) {
        return offlineSnapshotStreamService.subscribe(userId, deviceId);
    }
}
