package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.config.AppProperties;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final AppProperties properties;

    public HealthController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "service", "offline-pay-backend",
                "workerEnabled", properties.worker().enabled(),
                "now", OffsetDateTime.now().toString()
        );
    }
}

