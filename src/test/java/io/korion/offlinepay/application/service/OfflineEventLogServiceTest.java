package io.korion.offlinepay.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OfflineEventLogServiceTest {

    private DeviceRepository deviceRepository;
    private OfflineEventLogRepository offlineEventLogRepository;
    private OfflineEventLogService service;

    @BeforeEach
    void setUp() {
        deviceRepository = Mockito.mock(DeviceRepository.class);
        offlineEventLogRepository = Mockito.mock(OfflineEventLogRepository.class);
        service = new OfflineEventLogService(
                deviceRepository,
                offlineEventLogRepository,
                new JsonService(new ObjectMapper())
        );
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device()));
        when(offlineEventLogRepository.save(
                anyLong(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyString()
        )).thenReturn(eventLog());
    }

    @Test
    void acknowledgedRequestEventClosesPendingRequestLifecycle() {
        service.record(new OfflineEventLogService.RecordOfflineEventCommand(
                1L,
                "device-1",
                OfflineEventType.REQUEST_RECEIVED,
                OfflineEventStatus.ACKNOWLEDGED,
                "KORI",
                "TRC-20",
                BigDecimal.ONE,
                "request-1",
                null,
                "sender-device",
                "sender",
                null,
                "request received",
                Map.of()
        ));

        verify(offlineEventLogRepository).closePendingByRequestId(
                "request-1",
                OfflineEventStatus.ACKNOWLEDGED,
                null
        );
    }

    @Test
    void failedRequestEventClosesPendingRequestLifecycleAsFailed() {
        service.record(new OfflineEventLogService.RecordOfflineEventCommand(
                1L,
                "device-1",
                OfflineEventType.TRANSPORT_FAILED,
                OfflineEventStatus.FAILED,
                "KORI",
                "TRC-20",
                BigDecimal.ONE,
                "request-1",
                null,
                "sender-device",
                "sender",
                "BLE_PAIR_FAIL",
                "transport failed",
                Map.of()
        ));

        verify(offlineEventLogRepository).closePendingByRequestId(
                "request-1",
                OfflineEventStatus.FAILED,
                "BLE_PAIR_FAIL"
        );
    }

    @Test
    void pendingRequestEventDoesNotCloseLifecycle() {
        service.record(new OfflineEventLogService.RecordOfflineEventCommand(
                1L,
                "device-1",
                OfflineEventType.REQUEST_SENT,
                OfflineEventStatus.PENDING,
                "KORI",
                "TRC-20",
                BigDecimal.ONE,
                "request-1",
                null,
                "receiver-device",
                "receiver",
                null,
                "request sent",
                Map.of()
        ));

        verify(offlineEventLogRepository, never()).closePendingByRequestId(
                anyString(),
                any(),
                any()
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
                "event-1",
                1L,
                "device-1",
                OfflineEventType.REQUEST_RECEIVED,
                OfflineEventStatus.ACKNOWLEDGED,
                "KORI",
                "TRC-20",
                BigDecimal.ONE,
                "request-1",
                null,
                "counterparty-device",
                "counterparty",
                null,
                "ok",
                "{}",
                now,
                now
        );
    }
}
