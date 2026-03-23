package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.domain.model.Device;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OfflineSnapshotSignalService {

    private final DeviceRepository deviceRepository;
    private final OfflineSnapshotStreamService offlineSnapshotStreamService;

    public OfflineSnapshotSignalService(
            DeviceRepository deviceRepository,
            OfflineSnapshotStreamService offlineSnapshotStreamService
    ) {
        this.deviceRepository = deviceRepository;
        this.offlineSnapshotStreamService = offlineSnapshotStreamService;
    }

    public int publishWalletRefreshRequiredForUser(long userId, String assetCode, String reason) {
        List<Device> activeDevices = deviceRepository.findActiveByUserId(userId);
        for (Device device : activeDevices) {
            offlineSnapshotStreamService.publishWalletRefreshRequired(
                    userId,
                    device.deviceId(),
                    assetCode,
                    reason
            );
        }
        return activeDevices.size();
    }
}
