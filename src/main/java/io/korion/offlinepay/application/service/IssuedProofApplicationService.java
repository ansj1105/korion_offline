package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.application.port.SettlementRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralDeviceRebindCandidate;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssuedProofApplicationService {

    private static final String SUBJECT_BINDING_NAMESPACE = "korion-offline-pay:user-binding";

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final IssuedOfflineProofRepository issuedOfflineProofRepository;
    private final SettlementRepository settlementRepository;
    private final ProofIssuerSignatureService proofIssuerSignatureService;
    private final JsonService jsonService;
    private final AppProperties properties;

    public IssuedProofApplicationService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            IssuedOfflineProofRepository issuedOfflineProofRepository,
            SettlementRepository settlementRepository,
            ProofIssuerSignatureService proofIssuerSignatureService,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.issuedOfflineProofRepository = issuedOfflineProofRepository;
        this.settlementRepository = settlementRepository;
        this.proofIssuerSignatureService = proofIssuerSignatureService;
        this.jsonService = jsonService;
        this.properties = properties;
    }

    @Transactional
    public IssuedProofEnvelope issue(IssueCommand command) {
        String normalizedAssetCode = command.assetCode() == null || command.assetCode().isBlank()
                ? properties.assetCode()
                : command.assetCode().trim().toUpperCase();
        Device device = deviceRepository.findByUserIdAndDeviceId(command.userId(), command.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("device binding mismatch: " + command.deviceId()));
        List<CollateralLock> collaterals = resolveIssuableCollaterals(command.userId(), command.deviceId(), normalizedAssetCode);
        CollateralLock collateral = selectPrimaryCollateral(collaterals)
                .orElseThrow(() -> new IllegalArgumentException("collateral not found for asset: " + normalizedAssetCode));
        BigDecimal usableAmount = collaterals.stream()
                .map(CollateralLock::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OffsetDateTime issuedAt = OffsetDateTime.now();
        OffsetDateTime expiresAt = resolveAggregateExpiresAt(collaterals)
                .orElse(issuedAt.plusHours(properties.defaultCollateralExpiryHours()));
        String proofId = UUID.randomUUID().toString();
        String nonce = "proof_" + UUID.randomUUID().toString().replace("-", "");
        List<String> collateralLockIds = collaterals.stream()
                .map(CollateralLock::id)
                .toList();
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("proofId", proofId);
        payloadMap.put("userId", command.userId());
        payloadMap.put("subjectBindingKey", buildSubjectBindingKey(command.userId(), normalizedAssetCode));
        payloadMap.put("deviceId", command.deviceId());
        payloadMap.put("collateralLockId", collateral.id());
        payloadMap.put("primaryCollateralLockId", collateral.id());
        payloadMap.put("collateralLockIds", collateralLockIds);
        payloadMap.put("collateralCount", collateralLockIds.size());
        payloadMap.put("assetCode", normalizedAssetCode);
        payloadMap.put("usableAmount", usableAmount.toPlainString());
        payloadMap.put("issuedAt", issuedAt.toString());
        payloadMap.put("expiresAt", expiresAt.toString());
        payloadMap.put("nonce", nonce);
        payloadMap.put("devicePublicKey", device.publicKey());
        payloadMap.put("issuerKeyId", proofIssuerSignatureService.keyId());
        String payload = jsonService.write(payloadMap);
        String signature = proofIssuerSignatureService.sign(payload);
        IssuedOfflineProof issued = issuedOfflineProofRepository.save(
                proofId,
                command.userId(),
                command.deviceId(),
                collateral.id(),
                normalizedAssetCode,
                usableAmount,
                nonce,
                proofIssuerSignatureService.keyId(),
                proofIssuerSignatureService.publicKey(),
                signature,
                payload,
                IssuedProofStatus.ACTIVE,
                expiresAt
        );
        return new IssuedProofEnvelope(
                issued.id(),
                issued.userId(),
                issued.deviceId(),
                issued.collateralId(),
                issued.assetCode(),
                issued.usableAmount().toPlainString(),
                issued.proofNonce(),
                issued.issuerKeyId(),
                issued.issuerPublicKey(),
                issued.issuerSignature(),
                issued.issuedPayloadJson(),
                issued.expiresAt().toString(),
                issued.createdAt().toString(),
                collateralLockIds
        );
    }

    private List<CollateralLock> resolveIssuableCollaterals(long userId, String deviceId, String assetCode) {
        syncUserAssetCollateralsToDevice(userId, deviceId, assetCode, "ON_DEMAND_PROOF_ISSUE");
        return collateralRepository.findActiveByUserIdAndDeviceIdAndAssetCode(userId, deviceId, assetCode);
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.collateral-device-sync-delay-ms:60000}")
    @Transactional
    public void syncSingleActiveDeviceCollateralBindings() {
        if (properties.worker() == null || !properties.worker().enabled()) {
            return;
        }
        int batchSize = Math.max(1, properties.settlementStreamBatchSize());
        List<CollateralDeviceRebindCandidate> candidates = collateralRepository.findSingleActiveDeviceRebindCandidates(
                properties.assetCode(),
                batchSize
        );
        for (CollateralDeviceRebindCandidate candidate : candidates) {
            if (hasOpenSettlement(candidate.collateral())) {
                continue;
            }
            collateralRepository.rebindDevice(
                    candidate.collateral().id(),
                    candidate.collateral().deviceId(),
                    candidate.targetDeviceId(),
                    buildDeviceSyncMetadata(candidate.collateral().deviceId(), candidate.targetDeviceId(), "SINGLE_ACTIVE_DEVICE_WORKER")
            );
        }
    }

    private void syncUserAssetCollateralsToDevice(long userId, String targetDeviceId, String assetCode, String reason) {
        List<CollateralLock> collaterals = collateralRepository.findActiveByUserIdAndAssetCode(userId, assetCode);
        boolean foundMovableCollateral = false;
        boolean blockedBySettlement = false;
        for (CollateralLock collateral : collaterals) {
            if (hasOpenSettlement(collateral)) {
                blockedBySettlement = true;
                continue;
            }
            foundMovableCollateral = true;
            if (!collateral.deviceId().equals(targetDeviceId)) {
                rebindCollateralToCurrentDevice(collateral, targetDeviceId, reason);
            }
        }
        if (!foundMovableCollateral && blockedBySettlement) {
            throw new IllegalArgumentException("collateral device sync blocked: pending settlement exists");
        }
    }

    private void rebindCollateralToCurrentDevice(CollateralLock collateral, String targetDeviceId, String reason) {
        boolean updated = collateralRepository.rebindDevice(
                collateral.id(),
                collateral.deviceId(),
                targetDeviceId,
                buildDeviceSyncMetadata(collateral.deviceId(), targetDeviceId, reason)
        );
        if (!updated) {
            throw new IllegalArgumentException("collateral device sync failed: stale collateral device binding");
        }
    }

    private boolean hasOpenSettlement(CollateralLock collateral) {
        return settlementRepository.existsOpenByCollateralId(collateral.id());
    }

    private String buildDeviceSyncMetadata(String previousDeviceId, String targetDeviceId, String reason) {
        return jsonService.write(Map.of(
                "deviceSync", Map.of(
                        "reason", reason,
                        "previousDeviceId", previousDeviceId,
                        "targetDeviceId", targetDeviceId,
                        "syncedAt", OffsetDateTime.now().toString()
                )
        ));
    }

    private Optional<CollateralLock> selectPrimaryCollateral(List<CollateralLock> collaterals) {
        return collaterals.stream()
                .max(Comparator.comparing(CollateralLock::remainingAmount));
    }

    private Optional<OffsetDateTime> resolveAggregateExpiresAt(List<CollateralLock> collaterals) {
        return collaterals.stream()
                .map(CollateralLock::expiresAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder());
    }

    public static String buildSubjectBindingKey(long userId, String assetCode) {
        return sha256Hex(SUBJECT_BINDING_NAMESPACE + "|" + userId + "|" + String.valueOf(assetCode).toUpperCase());
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    public record IssueCommand(
            long userId,
            String deviceId,
            String assetCode
    ) {}

    public record IssuedProofEnvelope(
            String proofId,
            long userId,
            String deviceId,
            String collateralLockId,
            String assetCode,
            String usableAmount,
            String nonce,
            String issuerKeyId,
            String issuerPublicKey,
            String issuerSignature,
            String issuedPayload,
            String expiresAt,
            String issuedAt,
            List<String> collateralLockIds
    ) {}
}
