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
    private final OfflineSnapshotStreamService offlineSnapshotStreamService;

    public DeviceApplicationService(
            DeviceRepository deviceRepository,
            JsonService jsonService,
            OfflineSnapshotStreamService offlineSnapshotStreamService
    ) {
        this.deviceRepository = deviceRepository;
        this.jsonService = jsonService;
        this.offlineSnapshotStreamService = offlineSnapshotStreamService;
    }

    @Transactional
    public Device registerDevice(RegisterDeviceCommand command) {
        Device registered = deviceRepository.findByDeviceId(command.deviceId())
                .map(existing -> {
                    if (existing.userId() != command.userId()) {
                        throw new IllegalArgumentException("device already belongs to another user: " + command.deviceId());
                    }
                    if (!existing.publicKey().equals(command.publicKey())
                            || existing.keyVersion() != command.keyVersion()
                            || existing.status() != io.korion.offlinepay.domain.status.DeviceStatus.ACTIVE) {
                        return deviceRepository.refreshRegistration(
                                command.userId(),
                                command.deviceId(),
                                command.publicKey(),
                                command.keyVersion(),
                                jsonService.write(command.metadata())
                        );
                    }
                    return existing;
                })
                .orElseGet(() -> deviceRepository.save(
                        command.userId(),
                        command.deviceId(),
                        command.publicKey(),
                        command.keyVersion(),
                        jsonService.write(command.metadata())
                ));
        offlineSnapshotStreamService.publishDeviceRegistrationChanged(
                command.userId(),
                command.deviceId(),
                "REGISTERED"
        );
        return registered;
    }

    @Transactional
    public Device revokeDevice(RevokeDeviceCommand command) {
        deviceRepository.revoke(
                command.deviceId(),
                command.keyVersion(),
                jsonService.write(Map.of("reason", command.reason() == null ? "" : command.reason()))
        );
        Device revoked = deviceRepository.findByDeviceId(command.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("device not found: " + command.deviceId()));
        offlineSnapshotStreamService.publishDeviceRegistrationChanged(
                revoked.userId(),
                revoked.deviceId(),
                command.reason() == null ? "REVOKED" : command.reason()
        );
        return revoked;
    }

    @Transactional
    public Device updateDeviceProfile(UpdateDeviceProfileCommand command) {
        Device updated = deviceRepository.updateProfile(
                command.userId(),
                command.deviceId(),
                jsonService.write(command.metadata())
        );
        offlineSnapshotStreamService.publishDeviceProfileChanged(
                updated.userId(),
                updated.deviceId(),
                "PROFILE_UPDATED"
        );
        return updated;
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

    public record UpdateDeviceProfileCommand(
            long userId,
            String deviceId,
            Map<String, Object> metadata
    ) {}
}
