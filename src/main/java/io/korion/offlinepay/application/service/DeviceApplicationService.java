package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.domain.model.Device;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceApplicationService {

    private final DeviceRepository deviceRepository;
    private final JsonService jsonService;

    public DeviceApplicationService(DeviceRepository deviceRepository, JsonService jsonService) {
        this.deviceRepository = deviceRepository;
        this.jsonService = jsonService;
    }

    @Transactional
    public Device registerDevice(RegisterDeviceCommand command) {
        return deviceRepository.findByDeviceId(command.deviceId())
                .orElseGet(() -> deviceRepository.save(
                        command.userId(),
                        command.deviceId(),
                        command.publicKey(),
                        command.keyVersion(),
                        jsonService.write(command.metadata())
                ));
    }

    @Transactional
    public Device revokeDevice(RevokeDeviceCommand command) {
        deviceRepository.revoke(
                command.deviceId(),
                command.keyVersion(),
                jsonService.write(Map.of("reason", command.reason() == null ? "" : command.reason()))
        );
        return deviceRepository.findByDeviceId(command.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("device not found: " + command.deviceId()));
    }

    public record RegisterDeviceCommand(
            long userId,
            String deviceId,
            String publicKey,
            int keyVersion,
            Map<String, Object> metadata
    ) {}

    public record RevokeDeviceCommand(
            String deviceId,
            Integer keyVersion,
            String reason
    ) {}
}
