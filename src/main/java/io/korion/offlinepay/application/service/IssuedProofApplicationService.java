package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.IssuedOfflineProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssuedProofApplicationService {

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final IssuedOfflineProofRepository issuedOfflineProofRepository;
    private final ProofIssuerSignatureService proofIssuerSignatureService;
    private final JsonService jsonService;
    private final AppProperties properties;

    public IssuedProofApplicationService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            IssuedOfflineProofRepository issuedOfflineProofRepository,
            ProofIssuerSignatureService proofIssuerSignatureService,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.issuedOfflineProofRepository = issuedOfflineProofRepository;
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
        CollateralLock collateral = collateralRepository.findLatestByUserIdAndDeviceIdAndAssetCode(
                        command.userId(),
                        command.deviceId(),
                        normalizedAssetCode
                )
                .orElseThrow(() -> new IllegalArgumentException("collateral not found for asset: " + normalizedAssetCode));

        OffsetDateTime issuedAt = OffsetDateTime.now();
        OffsetDateTime expiresAt = collateral.expiresAt() != null
                ? collateral.expiresAt()
                : issuedAt.plusHours(properties.defaultCollateralExpiryHours());
        String proofId = UUID.randomUUID().toString();
        String nonce = "proof_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("proofId", proofId);
        payloadMap.put("userId", command.userId());
        payloadMap.put("deviceId", command.deviceId());
        payloadMap.put("collateralLockId", collateral.id());
        payloadMap.put("assetCode", normalizedAssetCode);
        payloadMap.put("usableAmount", collateral.remainingAmount().toPlainString());
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
                collateral.remainingAmount(),
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
                issued.createdAt().toString()
        );
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
            String issuedAt
    ) {}
}
