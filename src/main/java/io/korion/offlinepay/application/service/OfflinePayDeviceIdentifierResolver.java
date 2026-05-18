package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.domain.model.Device;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OfflinePayDeviceIdentifierResolver {

    private static final String APP_DEVICE_SUFFIX_PREFIX = "app-suffix:";
    private static final int APP_DEVICE_SUFFIX_LENGTH = 8;
    private static final String APP_DEVICE_SUFFIX_PATTERN = "^[0-9a-fA-F]{8}$";

    private final DeviceRepository deviceRepository;

    public OfflinePayDeviceIdentifierResolver(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Optional<Device> resolve(String deviceId) {
        Optional<Device> exact = deviceRepository.findByDeviceId(deviceId);
        if (exact.isPresent()) {
            return exact;
        }
        String suffix = extractAppDeviceSuffix(deviceId);
        return suffix.isBlank() ? Optional.empty() : deviceRepository.findUniqueActiveByDeviceIdSuffix(suffix);
    }

    String extractAppDeviceSuffix(String deviceId) {
        String normalized = deviceId == null ? "" : deviceId.trim();
        if (!normalized.startsWith(APP_DEVICE_SUFFIX_PREFIX)) {
            return "";
        }
        String suffix = normalized.substring(APP_DEVICE_SUFFIX_PREFIX.length());
        return suffix.length() == APP_DEVICE_SUFFIX_LENGTH && suffix.matches(APP_DEVICE_SUFFIX_PATTERN) ? suffix : "";
    }
}
