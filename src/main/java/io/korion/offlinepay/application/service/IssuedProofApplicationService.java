package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final ProofIssuerSignatureService proofIssuerSignatureService;
    private final JsonService jsonService;
    private final JsonPayloadCanonicalizationService jsonPayloadCanonicalizationService;
    private final AppProperties properties;

    public IssuedProofApplicationService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            IssuedOfflineProofRepository issuedOfflineProofRepository,
            ProofIssuerSignatureService proofIssuerSignatureService,
            JsonService jsonService,
            JsonPayloadCanonicalizationService jsonPayloadCanonicalizationService,
            AppProperties properties
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.issuedOfflineProofRepository = issuedOfflineProofRepository;
        this.proofIssuerSignatureService = proofIssuerSignatureService;
        this.jsonService = jsonService;
        this.jsonPayloadCanonicalizationService = jsonPayloadCanonicalizationService;
        this.properties = properties;
    }

    @Transactional
    public IssuedProofEnvelope issue(IssueCommand command) {
        String normalizedAssetCode = command.assetCode() == null || command.assetCode().isBlank()
                ? properties.assetCode()
                : command.assetCode().trim().toUpperCase();
        Device device = deviceRepository.findByUserIdAndDeviceId(command.userId(), command.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("device binding mismatch: " + command.deviceId()));
        OffsetDateTime issuedAt = OffsetDateTime.now();
        List<CollateralLock> collaterals = resolveIssuableCollaterals(command.userId(), normalizedAssetCode);
        CollateralLock collateral = selectPrimaryCollateral(collaterals)
                .orElseThrow(() -> new IllegalArgumentException("collateral unavailable for asset: " + normalizedAssetCode));
        BigDecimal usableAmount = collaterals.stream()
                .map(CollateralLock::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> collateralLockIds = collaterals.stream()
                .map(CollateralLock::id)
                .toList();
        Optional<IssuedOfflineProof> existingActive = issuedOfflineProofRepository
                .findLatestActiveByUserIdAndDeviceIdAndAssetCode(command.userId(), command.deviceId(), normalizedAssetCode);
        if (existingActive.isPresent()) {
            IssuedOfflineProof existing = existingActive.get();
            if (isReusableForCurrentCollateral(existing, collateral.id(), collateralLockIds, usableAmount)) {
                return new IssuedProofEnvelope(
                        existing.id(),
                        existing.userId(),
                        existing.deviceId(),
                        existing.collateralId(),
                        existing.assetCode(),
                        existing.usableAmount().toPlainString(),
                        existing.proofNonce(),
                        existing.issuerKeyId(),
                        existing.issuerPublicKey(),
                        existing.issuerSignature(),
                        existing.issuedPayloadJson(),
                        existing.status().name(),
                        formatExpiresAt(existing.expiresAt()),
                        existing.createdAt().toString(),
                        collateralLockIds
                );
            }
            issuedOfflineProofRepository.revokeActiveByUserIdAndDeviceIdAndAssetCode(
                    command.userId(),
                    command.deviceId(),
                    normalizedAssetCode
            );
        }

        OffsetDateTime expiresAt = null;
        String proofId = UUID.randomUUID().toString();
        String nonce = "proof_" + UUID.randomUUID().toString().replace("-", "");
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
        payloadMap.put("expiresAt", null);
        payloadMap.put("nonce", nonce);
        payloadMap.put("devicePublicKey", device.publicKey());
        payloadMap.put("issuerKeyId", proofIssuerSignatureService.keyId());
        String payload = jsonPayloadCanonicalizationService.canonicalize(jsonService.write(payloadMap));
        String signature = proofIssuerSignatureService.sign(payload);
        if (!proofIssuerSignatureService.verify(payload, proofIssuerSignatureService.publicKey(), signature)) {
            throw new IllegalStateException("issued proof self verification failed");
        }
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
                payload,
                issued.status().name(),
                formatExpiresAt(issued.expiresAt()),
                issued.createdAt().toString(),
                collateralLockIds
        );
    }

    private List<CollateralLock> resolveIssuableCollaterals(long userId, String assetCode) {
        return collateralRepository.findActiveByUserIdAndAssetCode(userId, assetCode);
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.collateral-device-sync-delay-ms:60000}")
    @Transactional
    public void syncSingleActiveDeviceCollateralBindings() {
        // Collateral ownership is user + active security-device authorization, not lock.device_id.
        // Keep the legacy scheduler inert so it cannot migrate collateral locks between devices.
    }

    private Optional<CollateralLock> selectPrimaryCollateral(List<CollateralLock> collaterals) {
        return collaterals.stream()
                .max(Comparator.comparing(CollateralLock::remainingAmount));
    }

    private boolean isReusableForCurrentCollateral(
            IssuedOfflineProof existing,
            String primaryCollateralId,
            List<String> currentCollateralLockIds,
            BigDecimal currentUsableAmount
    ) {
        if (!existing.collateralId().equals(primaryCollateralId)) {
            return false;
        }
        if (existing.usableAmount().compareTo(currentUsableAmount) != 0) {
            return false;
        }
        return readIssuedProofCollateralLockIds(existing).equals(new LinkedHashSet<>(currentCollateralLockIds));
    }

    private String formatExpiresAt(OffsetDateTime expiresAt) {
        return expiresAt == null ? "" : expiresAt.toString();
    }

    private Set<String> readIssuedProofCollateralLockIds(IssuedOfflineProof proof) {
        JsonNode payload = jsonService.readTree(proof.issuedPayloadJson());
        JsonNode lockIds = payload.path("collateralLockIds");
        Set<String> result = new LinkedHashSet<>();
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
        if (result.isEmpty() && proof.collateralId() != null && !proof.collateralId().isBlank()) {
            result.add(proof.collateralId());
        }
        return result;
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
            String status,
            String expiresAt,
            String issuedAt,
            List<String> collateralLockIds
    ) {}
}
