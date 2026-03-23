package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflineSnapshotService {

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final AppProperties properties;

    public OfflineSnapshotService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            AppProperties properties
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public CurrentSnapshot getCurrentSnapshot(long userId, String deviceId, String assetCode) {
        String normalizedAssetCode = assetCode == null || assetCode.isBlank()
                ? properties.assetCode()
                : assetCode.trim().toUpperCase();
        Device device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("device binding mismatch: " + deviceId));
        CollateralLock collateral = collateralRepository
                .findLatestByUserIdAndDeviceIdAndAssetCode(userId, deviceId, normalizedAssetCode)
                .orElse(null);

        return new CurrentSnapshot(
                userId,
                deviceId,
                normalizedAssetCode,
                new DeviceRegistrationSnapshot(
                        device.id(),
                        device.deviceId(),
                        device.userId(),
                        device.publicKey(),
                        device.keyVersion(),
                        device.status().name(),
                        device.updatedAt().toString()
                ),
                collateral == null ? null : new CollateralSnapshot(
                        collateral.id(),
                        collateral.userId(),
                        collateral.deviceId(),
                        collateral.assetCode(),
                        collateral.lockedAmount().toPlainString(),
                        collateral.remainingAmount().toPlainString(),
                        collateral.policyVersion(),
                        collateral.status().name(),
                        collateral.initialStateRoot(),
                        collateral.externalLockId(),
                        collateral.expiresAt() == null ? "" : collateral.expiresAt().toString(),
                        collateral.updatedAt().toString()
                ),
                true,
                OffsetDateTime.now().toString()
        );
    }

    public record CurrentSnapshot(
            long userId,
            String deviceId,
            String assetCode,
            DeviceRegistrationSnapshot deviceRegistration,
            CollateralSnapshot collateral,
            boolean walletRefreshRequired,
            String refreshedAt
    ) {}

    public record DeviceRegistrationSnapshot(
            String registrationId,
            String deviceId,
            long userId,
            String publicKey,
            int keyVersion,
            String status,
            String updatedAt
    ) {}

    public record CollateralSnapshot(
            String collateralId,
            long userId,
            String deviceId,
            String assetCode,
            String lockedAmount,
            String remainingAmount,
            int policyVersion,
            String status,
            String initialStateRoot,
            String externalLockId,
            String expiresAt,
            String updatedAt
    ) {}
}
