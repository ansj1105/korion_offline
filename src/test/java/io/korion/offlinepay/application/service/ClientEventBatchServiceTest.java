package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import io.korion.offlinepay.interfaces.http.dto.SubmitClientEventBatchRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ClientEventBatchServiceTest {

    private DeviceRepository deviceRepository;
    private OfflineEventLogRepository eventLogRepository;
    private OfflineEventLogService eventLogService;
    private SettlementApplicationService settlementApplicationService;
    private ClientEventBatchService service;

    @BeforeEach
    void setUp() {
        deviceRepository = Mockito.mock(DeviceRepository.class);
        eventLogRepository = Mockito.mock(OfflineEventLogRepository.class);
        eventLogService = Mockito.mock(OfflineEventLogService.class);
        settlementApplicationService = Mockito.mock(SettlementApplicationService.class);
        service = new ClientEventBatchService(
                deviceRepository,
                eventLogRepository,
                eventLogService,
                settlementApplicationService
        );
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device()));
        when(eventLogService.record(any())).thenReturn(eventLog());
    }

    @Test
    void skipsAlreadyUploadedClientEventId() {
        when(eventLogRepository.existsByClientEventId("event-1")).thenReturn(true);

        ClientEventBatchService.ClientEventBatchResponse response = service.submit(new SubmitClientEventBatchRequest(
                "device-1",
                "KORI",
                List.of(event("event-1"))
        ));

        assertEquals(1, response.duplicated());
        assertEquals("DUPLICATE", response.results().get(0).status());
        verify(eventLogService, never()).record(any());
        verify(settlementApplicationService, never()).submitBatch(any());
        verify(settlementApplicationService, never()).ingestLocalEvidence(any());
    }

    @Test
    void recordsAcceptedClientEventWithServerDeviceUser() {
        ClientEventBatchService.ClientEventBatchResponse response = service.submit(new SubmitClientEventBatchRequest(
                "device-1",
                "KORI",
                List.of(event("event-2"))
        ));

        assertEquals(1, response.accepted());
        assertEquals(1L, response.userId());
        assertEquals("ACCEPTED", response.results().get(0).status());
        verify(eventLogService).record(any(OfflineEventLogService.RecordOfflineEventCommand.class));
    }

    private static SubmitClientEventBatchRequest.ClientEventRequest event(String eventId) {
        return new SubmitClientEventBatchRequest.ClientEventRequest(
                eventId,
                "session-1",
                "request-1",
                null,
                "SEND",
                "REQUEST_SENT",
                "PENDING",
                "KORI",
                "mainnet",
                BigDecimal.ONE,
                "device-2",
                "receiver",
                null,
                "request sent",
                null,
                null,
                null,
                null,
                Map.of("source", "test")
        );
    }

    private static Device device() {
        OffsetDateTime now = OffsetDateTime.now();
        return new Device(
                "device-row-1",
                "device-1",
                1L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                now,
                now
        );
    }

    private static OfflineEventLog eventLog() {
        OffsetDateTime now = OffsetDateTime.now();
        return new OfflineEventLog(
                "event-log-1",
                1L,
                "device-1",
                OfflineEventType.REQUEST_SENT,
                OfflineEventStatus.PENDING,
                "KORI",
                "mainnet",
                BigDecimal.ONE,
                "request-1",
                null,
                "device-2",
                "receiver",
                null,
                "request sent",
                "{}",
                now,
                now
        );
    }
}
