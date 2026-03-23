package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeviceApplicationServiceTest {

    private final DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
    private final JsonService jsonService = new JsonService(new com.fasterxml.jackson.databind.ObjectMapper());
    private final OfflineSnapshotStreamService offlineSnapshotStreamService = Mockito.mock(OfflineSnapshotStreamService.class);
    private final DeviceApplicationService service = new DeviceApplicationService(
            deviceRepository,
            jsonService,
            offlineSnapshotStreamService
    );

    @Test
    void returnsExistingDeviceWhenAlreadyRegistered() {
        Device existing = new Device(
                "row-id",
                "device-abc",
                1L,
                "pub-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(deviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(existing));

        Device result = service.registerDevice(new DeviceApplicationService.RegisterDeviceCommand(
                1L,
                "device-abc",
                "pub-key",
                1,
                Map.of()
        ));

        assertNotNull(result);
        assertEquals("device-abc", result.deviceId());
        verify(deviceRepository, never()).save(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
    }
}
