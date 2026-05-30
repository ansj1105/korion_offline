package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.interfaces.http.dto.RecordClientTraceRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClientTraceReportService {

    private static final long DEDUPE_WINDOW_MS = 180_000L;
    private static final int MAX_STEPS = 300;

    private final TelegramAlertService telegramAlertService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Long> recentlySent = new ConcurrentHashMap<>();

    @Autowired
    public ClientTraceReportService(TelegramAlertService telegramAlertService, ObjectMapper objectMapper) {
        this(telegramAlertService, objectMapper, Clock.systemUTC());
    }

    ClientTraceReportService(TelegramAlertService telegramAlertService, ObjectMapper objectMapper, Clock clock) {
        this.telegramAlertService = telegramAlertService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ClientTraceReportResult record(RecordClientTraceRequest request) {
        long now = clock.millis();
        evictOldEntries(now);
        String dedupeKey = buildDedupeKey(request);
        Long lastSentAt = recentlySent.get(dedupeKey);
        if (lastSentAt != null && now - lastSentAt < DEDUPE_WINDOW_MS) {
            return new ClientTraceReportResult(true, true);
        }

        String summary = buildSummary(request);
        String filename = buildFilename(request);
        telegramAlertService.notifyClientTrace(summary, filename, buildJsonPayload(request));
        recentlySent.put(dedupeKey, now);
        return new ClientTraceReportResult(true, false);
    }

    private void evictOldEntries(long now) {
        recentlySent.entrySet().removeIf(entry -> now - entry.getValue() > DEDUPE_WINDOW_MS);
    }

    private String buildDedupeKey(RecordClientTraceRequest request) {
        return normalize(request.traceId()) + "|"
                + normalize(request.sessionId()) + "|"
                + normalize(request.failureCode());
    }

    private String buildSummary(RecordClientTraceRequest request) {
        List<String> missing = resolveMissingMessages(request);
        return "[KORION] offline_pay client trace\n"
                + "traceId=" + normalize(request.traceId()) + "\n"
                + "userId=" + normalize(request.userId()) + "\n"
                + "deviceId=" + normalize(request.deviceId()) + "\n"
                + "sessionId=" + normalize(request.sessionId()) + "\n"
                + "stage=" + normalize(request.stage()) + "\n"
                + "entryAction=" + normalize(request.entryAction()) + "\n"
                + "messageType=" + normalize(request.messageType()) + "\n"
                + "localSagaStatus=" + normalize(request.localSagaStatus()) + "\n"
                + "failureCode=" + normalize(request.failureCode()) + "\n"
                + "nativeError=" + normalize(request.nativeError()) + "\n"
                + "missingMessages=" + (missing.isEmpty() ? "none" : String.join(",", missing));
    }

    private String buildFilename(RecordClientTraceRequest request) {
        String traceId = normalize(request.traceId()).replaceAll("[^a-zA-Z0-9._-]", "_");
        return "offline-pay-client-trace-" + traceId + ".json";
    }

    private String buildJsonPayload(RecordClientTraceRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receivedAt", Instant.now(clock).toString());
        payload.put("traceId", request.traceId());
        payload.put("sessionId", request.sessionId());
        payload.put("failureCode", request.failureCode());
        payload.put("flow", request.flow());
        payload.put("status", request.status());
        payload.put("stage", request.stage());
        payload.put("entryAction", request.entryAction());
        payload.put("messageType", request.messageType());
        payload.put("routePeerId", request.routePeerId());
        payload.put("localSagaStatus", request.localSagaStatus());
        payload.put("nativeError", request.nativeError());
        payload.put("deviceModel", request.deviceModel());
        payload.put("appVersion", request.appVersion());
        payload.put("platform", request.platform());
        payload.put("userId", request.userId());
        payload.put("deviceId", request.deviceId());
        payload.put("message", request.message());
        payload.put("metadata", request.metadata() == null ? Map.of() : request.metadata());
        payload.put("steps", trimSteps(request.steps()));
        payload.put("createdAt", request.createdAt());
        payload.put("missingMessages", resolveMissingMessages(request));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            return "{\"traceId\":\"" + normalize(request.traceId()) + "\",\"error\":\"json serialization failed\"}";
        }
    }

    private List<RecordClientTraceRequest.ClientTraceStep> trimSteps(List<RecordClientTraceRequest.ClientTraceStep> steps) {
        if (steps == null || steps.size() <= MAX_STEPS) {
            return steps == null ? List.of() : steps;
        }
        return steps.subList(steps.size() - MAX_STEPS, steps.size());
    }

    private List<String> resolveMissingMessages(RecordClientTraceRequest request) {
        String joined = String.join("\n", trimSteps(request.steps()).stream()
                .map(step -> (normalize(step.message()) + " " + normalize(step.metadata())).toUpperCase(Locale.ROOT))
                .toList());
        return List.of("REQUEST", "ACK", "APPROVE", "COMPLETE").stream()
                .filter(message -> !joined.contains(message))
                .toList();
    }

    private String normalize(Object value) {
        if (value == null) {
            return "unknown";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "unknown" : text;
    }

    public record ClientTraceReportResult(boolean accepted, boolean throttled) {}
}
