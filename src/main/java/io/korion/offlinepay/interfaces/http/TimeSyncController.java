package io.korion.offlinepay.interfaces.http;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/time")
public class TimeSyncController {

    @GetMapping("/sync")
    public TimeSyncResponse sync(
            @RequestHeader(value = "X-Client-Time-Zone", required = false) String clientTimeZone,
            @RequestHeader(value = "X-Client-Time-Zone-Offset-Minutes", required = false) Integer clientTimeZoneOffsetMinutes
    ) {
        Instant now = Instant.now();
        return new TimeSyncResponse(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.atOffset(ZoneOffset.UTC)),
                now.toEpochMilli(),
                "UTC",
                normalizeClientTimeZone(clientTimeZone),
                clientTimeZoneOffsetMinutes == null ? 0 : clientTimeZoneOffsetMinutes
        );
    }

    private String normalizeClientTimeZone(String value) {
        if (value == null || value.isBlank()) {
            return "UTC";
        }
        return value.trim();
    }

    public record TimeSyncResponse(
            String serverTime,
            long serverEpochMs,
            String serverTimeZone,
            String clientTimeZone,
            int clientTimeZoneOffsetMinutes
    ) {
    }
}
