package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OfflinePayDeviceIdentifierResolverTest {

    private final DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
    private final OfflinePayDeviceIdentifierResolver resolver = new OfflinePayDeviceIdentifierResolver(deviceRepository);

    @Test
    void resolvesExactDeviceIdFirst() {
        Device device = device("device-1");
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));

        Optional<Device> resolved = resolver.resolve("device-1");

        assertTrue(resolved.isPresent());
        assertEquals("device-1", resolved.get().deviceId());
        verify(deviceRepository, never()).findUniqueActiveByDeviceIdSuffix(Mockito.anyString());
    }

    @Test
    void resolvesAppSuffixToUniqueActiveDevice() {
        Device device = device("98db6beb-4ae1-4027-b9ee-507ce7eaeaa7");
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(device));

        Optional<Device> resolved = resolver.resolve("app-suffix:e7eaeaa7");

        assertTrue(resolved.isPresent());
        assertEquals("98db6beb-4ae1-4027-b9ee-507ce7eaeaa7", resolved.get().deviceId());
    }

    @Test
    void ignoresNonSuffixFallbacks() {
        when(deviceRepository.findByDeviceId("unknown-device")).thenReturn(Optional.empty());

        Optional<Device> resolved = resolver.resolve("unknown-device");

        assertTrue(resolved.isEmpty());
        verify(deviceRepository, never()).findUniqueActiveByDeviceIdSuffix(Mockito.anyString());
    }

    @Test
    void rejectsMalformedAppSuffixes() {
        when(deviceRepository.findByDeviceId("app-suffix:7")).thenReturn(Optional.empty());

        Optional<Device> resolved = resolver.resolve("app-suffix:7");

        assertTrue(resolved.isEmpty());
        verify(deviceRepository, never()).findUniqueActiveByDeviceIdSuffix(Mockito.anyString());
    }

    private Device device(String deviceId) {
        return new Device(
                "row-1",
                deviceId,
                39L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
