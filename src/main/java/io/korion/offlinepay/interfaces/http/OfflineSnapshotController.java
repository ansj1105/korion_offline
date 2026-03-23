package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.OfflineSnapshotService;
import io.korion.offlinepay.application.service.OfflineSnapshotSignalService;
import io.korion.offlinepay.application.service.OfflineSnapshotStreamService;
import io.korion.offlinepay.config.AppProperties;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/snapshots")
public class OfflineSnapshotController {

    private final OfflineSnapshotService offlineSnapshotService;
    private final OfflineSnapshotStreamService offlineSnapshotStreamService;
    private final OfflineSnapshotSignalService offlineSnapshotSignalService;
    private final AppProperties properties;

    public OfflineSnapshotController(
            OfflineSnapshotService offlineSnapshotService,
            OfflineSnapshotStreamService offlineSnapshotStreamService,
            OfflineSnapshotSignalService offlineSnapshotSignalService,
            AppProperties properties
    ) {
        this.offlineSnapshotService = offlineSnapshotService;
        this.offlineSnapshotStreamService = offlineSnapshotStreamService;
        this.offlineSnapshotSignalService = offlineSnapshotSignalService;
        this.properties = properties;
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

    @PostMapping("/internal/wallet-refresh")
    public ResponseEntity<Map<String, Object>> notifyWalletRefresh(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @RequestBody WalletRefreshRequest request
    ) {
        if (properties.foxCoin().apiKey() == null
                || properties.foxCoin().apiKey().isBlank()
                || !properties.foxCoin().apiKey().equals(apiKey)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized");
        }
        int publishedDeviceCount = offlineSnapshotSignalService.publishWalletRefreshRequiredForUser(
                request.userId(),
                request.assetCode(),
                request.reason()
        );
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "publishedDeviceCount", publishedDeviceCount
        ));
    }

    public record WalletRefreshRequest(
            long userId,
            String assetCode,
            String reason
    ) {}
}
