package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.policy.SettlementPolicyConstants;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflineSnapshotService {

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final IssuedOfflineProofRepository issuedOfflineProofRepository;
    private final FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort;
    private final AppProperties properties;
    private final JsonService jsonService;

    public OfflineSnapshotService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            IssuedOfflineProofRepository issuedOfflineProofRepository,
            FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort,
            AppProperties properties,
            JsonService jsonService
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.issuedOfflineProofRepository = issuedOfflineProofRepository;
        this.foxCoinWalletSnapshotPort = foxCoinWalletSnapshotPort;
        this.properties = properties;
        this.jsonService = jsonService;
    }

    @Transactional(readOnly = true)
    public CurrentSnapshot getCurrentSnapshot(long userId, String deviceId, String assetCode) {
        String normalizedAssetCode = assetCode == null || assetCode.isBlank()
                ? properties.assetCode()
                : assetCode.trim().toUpperCase();
        Device device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElse(null);
        CollateralLock collateral = collateralRepository
                .findAggregateByUserIdAndAssetCode(userId, normalizedAssetCode)
                .orElse(null);
        IssuedOfflineProof issuedProof = device == null
                ? null
                : issuedOfflineProofRepository
                        .findLatestActiveByUserIdAndDeviceIdAndAssetCode(userId, deviceId, normalizedAssetCode)
                        .orElse(null);
        WalletSnapshot walletSnapshot = loadWalletSnapshot(
                userId,
                normalizedAssetCode,
                collateral == null ? null : collateral.lockedAmount()
        );

        return new CurrentSnapshot(
                userId,
                deviceId,
                normalizedAssetCode,
                buildDeviceRegistrationSnapshot(userId, deviceId, device),
                collateral == null ? null : new CollateralSnapshot(
                        collateral.id(),
                        collateral.userId(),
                        deviceId,
                        collateral.assetCode(),
                        collateral.lockedAmount().toPlainString(),
                        collateral.remainingAmount().toPlainString(),
                        collateral.policyVersion(),
                        collateral.status().name(),
                        collateral.initialStateRoot(),
                        collateral.externalLockId(),
                        collateral.expiresAt() == null ? "" : collateral.expiresAt().toString(),
                        collateral.updatedAt().toString(),
                        collateral.updatedAt().toInstant().toEpochMilli()
                ),
                issuedProof == null ? null : new IssuedProofSnapshot(
                        issuedProof.id(),
                        issuedProof.collateralId(),
                        issuedProof.assetCode(),
                        issuedProof.usableAmount().toPlainString(),
                        issuedProof.proofNonce(),
                        issuedProof.issuerKeyId(),
                        issuedProof.issuerPublicKey(),
                        issuedProof.issuerSignature(),
                        issuedProof.issuedPayloadJson(),
                        issuedProof.status().name(),
                        issuedProof.expiresAt().toString(),
                        issuedProof.createdAt().toString()
                ),
                walletSnapshot,
                true,
                OffsetDateTime.now().toString(),
                SettlementPolicyConstants.COLLATERAL_SNAPSHOT_STALE_AFTER_MS
        );
    }

    private DeviceRegistrationSnapshot buildDeviceRegistrationSnapshot(long userId, String deviceId, Device device) {
        if (device == null) {
            return new DeviceRegistrationSnapshot(
                    "",
                    deviceId,
                    userId,
                    "",
                    0,
                    "",
                    "",
                    "WEB",
                    "UNREGISTERED",
                    "{}",
                    OffsetDateTime.now().toString()
            );
        }

        return new DeviceRegistrationSnapshot(
                device.id(),
                device.deviceId(),
                device.userId(),
                device.publicKey(),
                device.keyVersion(),
                readMetadataText(device, "nickname"),
                readMetadataText(device, "deviceModel"),
                readMetadataText(device, "platform"),
                device.status().name(),
                device.metadataJson(),
                device.updatedAt().toString()
        );
    }

    public record CurrentSnapshot(
            long userId,
            String deviceId,
            String assetCode,
            DeviceRegistrationSnapshot deviceRegistration,
            CollateralSnapshot collateral,
            IssuedProofSnapshot issuedProof,
            WalletSnapshot wallet,
            boolean walletRefreshRequired,
            String refreshedAt,
            long staleAfterMs
    ) {}

    public record DeviceRegistrationSnapshot(
            String registrationId,
            String deviceId,
            long userId,
            String publicKey,
            int keyVersion,
            String nickname,
            String deviceModel,
            String platform,
            String status,
            String metadataJson,
            String updatedAt
    ) {}

    public record IssuedProofSnapshot(
            String proofId,
            String collateralId,
            String assetCode,
            String usableAmount,
            String nonce,
            String issuerKeyId,
            String issuerPublicKey,
            String issuerSignature,
            String issuedPayload,
            String status,
            String expiresAt,
            String issuedAt
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
            String updatedAt,
            long snapshotVersion
    ) {}

    public record WalletSnapshot(
            long userId,
            String assetCode,
            String totalBalance,
            String lockedBalance,
            String additionalCollateralAvailableAmount,
            String canonicalBasis,
            String refreshedAt
    ) {}

    private WalletSnapshot loadWalletSnapshot(long userId, String assetCode, java.math.BigDecimal collateralLockedAmount) {
        try {
            FoxCoinWalletSnapshotPort.WalletSnapshot snapshot =
                    foxCoinWalletSnapshotPort.getCanonicalWalletSnapshot(userId, assetCode);
            java.math.BigDecimal currentCollateralAmount = collateralLockedAmount == null
                    ? java.math.BigDecimal.ZERO
                    : collateralLockedAmount.max(java.math.BigDecimal.ZERO);
            java.math.BigDecimal additionalCollateralAvailableAmount = snapshot.totalBalance()
                    .subtract(currentCollateralAmount)
                    .max(java.math.BigDecimal.ZERO);
            return new WalletSnapshot(
                    snapshot.userId(),
                    snapshot.assetCode(),
                    snapshot.totalBalance().toPlainString(),
                    snapshot.lockedBalance().toPlainString(),
                    additionalCollateralAvailableAmount.toPlainString(),
                    snapshot.canonicalBasis(),
                    snapshot.refreshedAt()
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readMetadataText(Device device, String fieldName) {
        try {
            JsonNode root = jsonService.readTree(device.metadataJson());
            JsonNode node = root.path(fieldName);
            if (node.isMissingNode() || node.isNull()) {
                return "";
            }
            return node.asText("");
        } catch (Exception ignored) {
            return "";
        }
    }
}
