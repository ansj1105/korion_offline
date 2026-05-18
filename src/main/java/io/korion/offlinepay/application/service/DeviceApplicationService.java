package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CoinManageDeviceSyncPort;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeviceApplicationService.class);

    private final DeviceRepository deviceRepository;
    private final JsonService jsonService;
    private final OfflineSnapshotStreamService offlineSnapshotStreamService;
    private final CoinManageDeviceSyncPort coinManageDeviceSyncPort;

    public DeviceApplicationService(
            DeviceRepository deviceRepository,
            JsonService jsonService,
            OfflineSnapshotStreamService offlineSnapshotStreamService,
            CoinManageDeviceSyncPort coinManageDeviceSyncPort
    ) {
        this.deviceRepository = deviceRepository;
        this.jsonService = jsonService;
        this.offlineSnapshotStreamService = offlineSnapshotStreamService;
        this.coinManageDeviceSyncPort = coinManageDeviceSyncPort;
    }

    @Transactional
    public Device registerDevice(RegisterDeviceCommand command) {
        Device registered = deviceRepository.findByDeviceId(command.deviceId())
                .map(existing -> {
                    if (existing.userId() != command.userId()
                            || !existing.publicKey().equals(command.publicKey())
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
        if (registered.userId() != command.userId()) {
            throw new IllegalStateException("device registration user mismatch after refresh: " + command.deviceId());
        }
        offlineSnapshotStreamService.publishDeviceRegistrationChanged(
                command.userId(),
                command.deviceId(),
                "REGISTERED"
        );
        syncCoinManageDevice(registered, DeviceStatus.ACTIVE);
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
        syncCoinManageDevice(revoked, DeviceStatus.REVOKED);
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
        syncCoinManageDevice(updated, updated.status());
        return updated;
    }

    private void syncCoinManageDevice(Device device, DeviceStatus status) {
        String coinManageStatus = toCoinManageDeviceStatus(status);
        try {
            coinManageDeviceSyncPort.upsertDevice(new CoinManageDeviceSyncPort.DeviceSyncCommand(
                    device.userId(),
                    device.deviceId(),
                    coinManageStatus,
                    device.keyVersion()
            ));
        } catch (RuntimeException exception) {
            log.warn("coin_manage offline pay device sync failed: deviceId={}, status={}", device.deviceId(), status, exception);
        }
    }

    private String toCoinManageDeviceStatus(DeviceStatus status) {
        return status == DeviceStatus.ACTIVE ? "ACTIVE" : "REVOKED";
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
