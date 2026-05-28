package io.korion.offlinepay.application.service;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.interfaces.http.dto.RecordClientTraceRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ClientTraceReportServiceTest {

    @Test
    void sendsTelegramSummaryAndJsonFile() {
        TelegramAlertService telegramAlertService = Mockito.mock(TelegramAlertService.class);
        ClientTraceReportService service = new ClientTraceReportService(
                telegramAlertService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC)
        );

        service.record(request("trace-1", "req-1", "BLE_PAIR_FAIL_133"));

        verify(telegramAlertService).notifyClientTrace(
                contains("traceId=trace-1"),
                eq("offline-pay-client-trace-trace-1.json"),
                contains("\"failureCode\" : \"BLE_PAIR_FAIL_133\"")
        );
    }

    @Test
    void throttlesDuplicateTraceReports() {
        TelegramAlertService telegramAlertService = Mockito.mock(TelegramAlertService.class);
        ClientTraceReportService service = new ClientTraceReportService(
                telegramAlertService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC)
        );

        service.record(request("trace-1", "req-1", "REQUEST_SESSION_FAILED"));
        ClientTraceReportService.ClientTraceReportResult second =
                service.record(request("trace-1", "req-1", "REQUEST_SESSION_FAILED"));

        verify(telegramAlertService, times(1)).notifyClientTrace(
                contains("traceId=trace-1"),
                eq("offline-pay-client-trace-trace-1.json"),
                contains("\"sessionId\" : \"req-1\"")
        );
        org.junit.jupiter.api.Assertions.assertTrue(second.throttled());
    }

    private static RecordClientTraceRequest request(String traceId, String sessionId, String failureCode) {
        return new RecordClientTraceRequest(
                traceId,
                sessionId,
                failureCode,
                "BLE_TRANSFER",
                "FAILED",
                "FORM",
                "SEND",
                "REQUEST",
                "peer-1",
                "REQUEST_SENT",
                "GATT_ERROR(133)",
                "Galaxy",
                "1.0.0",
                "android",
                1L,
                "device-1",
                "failed",
                Map.of("reasonCode", failureCode),
                List.of(new RecordClientTraceRequest.ClientTraceStep(
                        "step-1",
                        "2026-05-28T00:00:00Z",
                        "error",
                        "BLE REQUEST failed",
                        Map.of("envelopeType", "REQUEST")
                )),
                "2026-05-28T00:00:00Z"
        );
    }
}
