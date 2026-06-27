package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.policy.SettlementPolicyConstants;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflineSnapshotService {

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final IssuedOfflineProofRepository issuedOfflineProofRepository;
    private final OfflinePaymentProofRepository proofRepository;
    private final FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort;
    private final CoinManageCollateralPort coinManageCollateralPort;
    private final AppProperties properties;
    private final JsonService jsonService;
    private final JsonPayloadCanonicalizationService jsonPayloadCanonicalizationService;
    private final ProofIssuerSignatureService proofIssuerSignatureService;

    public OfflineSnapshotService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            IssuedOfflineProofRepository issuedOfflineProofRepository,
            OfflinePaymentProofRepository proofRepository,
            FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort,
            CoinManageCollateralPort coinManageCollateralPort,
            AppProperties properties,
            JsonService jsonService,
            JsonPayloadCanonicalizationService jsonPayloadCanonicalizationService,
            ProofIssuerSignatureService proofIssuerSignatureService
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.issuedOfflineProofRepository = issuedOfflineProofRepository;
        this.proofRepository = proofRepository;
        this.foxCoinWalletSnapshotPort = foxCoinWalletSnapshotPort;
        this.coinManageCollateralPort = coinManageCollateralPort;
        this.properties = properties;
        this.jsonService = jsonService;
        this.jsonPayloadCanonicalizationService = jsonPayloadCanonicalizationService;
        this.proofIssuerSignatureService = proofIssuerSignatureService;
    }

    @Transactional(readOnly = true)
    public CurrentSnapshot getCurrentSnapshot(long userId, String deviceId, String assetCode) {
        return getCurrentSnapshot(userId, deviceId, assetCode, null, null);
    }

    @Transactional(readOnly = true)
    public CurrentSnapshot getCurrentSnapshot(
            long userId,
            String deviceId,
            String assetCode,
            String clientTimeZone,
            Integer clientTimeZoneOffsetMinutes
    ) {
        String normalizedAssetCode = assetCode == null || assetCode.isBlank()
                ? properties.assetCode()
                : assetCode.trim().toUpperCase();
        String normalizedClientTimeZone = normalizeClientTimeZone(clientTimeZone);
        Integer normalizedClientTimeZoneOffsetMinutes = clientTimeZoneOffsetMinutes == null
                ? 0
                : clientTimeZoneOffsetMinutes;
        Device device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElse(null);
        CollateralLock collateral = collateralRepository
                .findAggregateByUserIdAndAssetCode(userId, normalizedAssetCode)
                .orElse(null);
        IssuedOfflineProof issuedProof = device == null
                ? null
                : issuedOfflineProofRepository
                        .findLatestActiveByUserIdAndDeviceIdAndAssetCode(userId, deviceId, normalizedAssetCode)
                        .filter(this::hasValidIssuerSignature)
                        .filter(proof -> isUsableForCurrentCollateral(proof, userId, normalizedAssetCode, collateral))
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
                        collateral.lockedAmount().toPlainString(),
                        "0",
                        collateral.remainingAmount().toPlainString(),
                        collateral.policyVersion(),
                        collateral.status().name(),
                        collateral.initialStateRoot(),
                        collateral.externalLockId(),
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
                        formatExpiresAt(issuedProof.expiresAt()),
                        issuedProof.createdAt().toString()
                ),
                walletSnapshot,
                true,
                OffsetDateTime.now().toString(),
                SettlementPolicyConstants.COLLATERAL_SNAPSHOT_STALE_AFTER_MS,
                "UTC",
                normalizedClientTimeZone,
                normalizedClientTimeZoneOffsetMinutes
        );
    }

    private String normalizeClientTimeZone(String clientTimeZone) {
        if (clientTimeZone == null || clientTimeZone.isBlank()) {
            return "UTC";
        }
        try {
            return ZoneId.of(clientTimeZone.trim()).getId();
        } catch (ZoneRulesException | IllegalArgumentException exception) {
            return "UTC";
        }
    }

    private boolean hasValidIssuerSignature(IssuedOfflineProof issuedProof) {
        return proofIssuerSignatureService.verify(
                issuedProof.issuedPayloadJson(),
                issuedProof.issuerPublicKey(),
                issuedProof.issuerSignature()
        ) || proofIssuerSignatureService.verify(
                jsonPayloadCanonicalizationService.canonicalize(issuedProof.issuedPayloadJson()),
                issuedProof.issuerPublicKey(),
                issuedProof.issuerSignature()
        );
    }

    private boolean isUsableForCurrentCollateral(
            IssuedOfflineProof proof,
            long userId,
            String assetCode,
            CollateralLock aggregateCollateral
    ) {
        if (aggregateCollateral == null || aggregateCollateral.remainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (proof.usableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        List<CollateralLock> activeCollaterals = collateralRepository.findActiveByUserIdAndAssetCode(userId, assetCode)
                .stream()
                .filter(collateral -> collateral.remainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        BigDecimal activeAmount = activeCollaterals.stream()
                .map(CollateralLock::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (proof.usableAmount().compareTo(activeAmount) != 0) {
            return false;
        }
        Set<String> activeCollateralIds = activeCollaterals.stream()
                .map(CollateralLock::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return activeCollateralIds.contains(proof.collateralId())
                && readIssuedProofCollateralLockIds(proof).equals(activeCollateralIds);
    }

    private String formatExpiresAt(OffsetDateTime expiresAt) {
        return expiresAt == null ? "" : expiresAt.toString();
    }

    private Set<String> readIssuedProofCollateralLockIds(IssuedOfflineProof proof) {
        Set<String> result = new LinkedHashSet<>();
        try {
            JsonNode payload = jsonService.readTree(proof.issuedPayloadJson());
            JsonNode lockIds = payload.path("collateralLockIds");
            if (lockIds.isArray()) {
                lockIds.forEach(node -> {
                    String id = node.asText("");
                    if (!id.isBlank()) {
                        result.add(id);
                    }
                });
            }
            if (result.isEmpty()) {
                String legacyLockId = payload.path("collateralLockId").asText("");
                if (!legacyLockId.isBlank()) {
                    result.add(legacyLockId);
                }
            }
        } catch (Exception ignored) {
            // Fall back to the repository collateral id below.
        }
        if (result.isEmpty() && proof.collateralId() != null && !proof.collateralId().isBlank()) {
            result.add(proof.collateralId());
        }
        return result;
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
            long staleAfterMs,
            String serverTimeZone,
            String clientTimeZone,
            int clientTimeZoneOffsetMinutes
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
            String collateralTotal,
            String unsettledOutgoing,
            String availableForPay,
            int policyVersion,
            String status,
            String initialStateRoot,
            String externalLockId,
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
            java.math.BigDecimal additionalCollateralAvailableAmount =
                    CollateralAvailabilityCalculator.resolveAdditionalCollateralAvailableAmount(
                            snapshot,
                            currentCollateralAmount
                    );
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

    // Checkpoint validity window — client should refresh on every server sync.
    private static final long CHECKPOINT_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;

    @Transactional(readOnly = true)
    public TrustedCheckpoint generateCheckpoint(String senderDeviceId, String assetCode) {
        var latestProof = proofRepository.findLatestSequenceAnchorBySenderDeviceId(senderDeviceId);
        String stateHash = latestProof.map(io.korion.offlinepay.domain.model.OfflinePaymentProof::hashChainHead).orElse("GENESIS");
        long counter = latestProof.map(io.korion.offlinepay.domain.model.OfflinePaymentProof::counter).orElse(0L);
        long maxOfflineTxSequence = proofRepository.findMaxSenderOfflineTxSequence(senderDeviceId);
        String normalizedAsset = assetCode == null || assetCode.isBlank() ? "KORI" : assetCode.trim().toUpperCase();
        long issuedAtMs = System.currentTimeMillis();
        long expiresAtMs = issuedAtMs + CHECKPOINT_EXPIRY_MS;
        String signingPayload = "CHECKPOINT_V1|" + senderDeviceId + "|" + normalizedAsset
                + "|" + stateHash + "|" + counter + "|" + maxOfflineTxSequence + "|" + issuedAtMs + "|" + expiresAtMs;
        String signature = proofIssuerSignatureService.sign(signingPayload);
        return new TrustedCheckpoint(
                senderDeviceId, normalizedAsset, stateHash, counter,
                maxOfflineTxSequence,
                issuedAtMs, expiresAtMs,
                proofIssuerSignatureService.keyId(),
                proofIssuerSignatureService.publicKey(),
                signature
        );
    }

    public record TrustedCheckpoint(
            String deviceId,
            String assetCode,
            String stateHash,
            long counter,
            long maxOfflineTxSequence,
            long issuedAtMs,
            long expiresAtMs,
            String issuerKeyId,
            String issuerPublicKey,
            String signature
    ) {}
}
