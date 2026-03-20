package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.CollateralStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollateralApplicationService {

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final CoinManageCollateralPort coinManageCollateralPort;
    private final JsonService jsonService;
    private final AppProperties properties;

    public CollateralApplicationService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            CoinManageCollateralPort coinManageCollateralPort,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.coinManageCollateralPort = coinManageCollateralPort;
        this.jsonService = jsonService;
        this.properties = properties;
    }

    @Transactional
    public CollateralLock createCollateral(CreateCollateralCommand command) {
        deviceRepository.findByDeviceId(command.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("device not registered: " + command.deviceId()));

        String assetCode = command.assetCode() == null || command.assetCode().isBlank()
                ? properties.assetCode()
                : command.assetCode();
        int policyVersion = command.policyVersion() == null ? 1 : command.policyVersion();
        String initialStateRoot = command.initialStateRoot() == null || command.initialStateRoot().isBlank()
                ? "GENESIS"
                : command.initialStateRoot();

        CoinManageCollateralPort.LockCollateralResult external = coinManageCollateralPort.lockCollateral(
                command.userId(),
                command.deviceId(),
                assetCode,
                command.amount(),
                command.deviceId(),
                policyVersion
        );

        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(properties.defaultCollateralExpiryHours());
        return collateralRepository.save(
                command.userId(),
                command.deviceId(),
                assetCode,
                command.amount(),
                command.amount(),
                initialStateRoot,
                policyVersion,
                CollateralStatus.LOCKED,
                external.lockId(),
                expiresAt,
                jsonService.write(command.metadata())
        );
    }

    @Transactional(readOnly = true)
    public CollateralLock getCollateral(String collateralId) {
        return collateralRepository.findById(collateralId)
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + collateralId));
    }

    @Transactional
    public CollateralLock releaseCollateral(String collateralId, ReleaseCollateralCommand command) {
        CollateralLock collateral = collateralRepository.findById(collateralId)
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + collateralId));

        if (collateral.userId() != command.userId()) {
            throw new IllegalArgumentException("collateral user mismatch: " + collateralId);
        }
        if (!collateral.deviceId().equals(command.deviceId())) {
            throw new IllegalArgumentException("collateral device mismatch: " + collateralId);
        }
        if (collateral.status() == CollateralStatus.RELEASED) {
            return collateral;
        }
        if (collateral.remainingAmount().signum() <= 0) {
            throw new IllegalArgumentException("collateral remaining amount is empty: " + collateralId);
        }

        String referenceId = "release:" + collateralId;
        coinManageCollateralPort.releaseCollateral(
                collateral.userId(),
                collateral.deviceId(),
                collateral.id(),
                collateral.assetCode(),
                collateral.remainingAmount(),
                referenceId
        );

        collateralRepository.deductRemainingAmount(collateral.id(), collateral.remainingAmount());
        collateralRepository.updateStatus(
                collateral.id(),
                CollateralStatus.RELEASED,
                jsonService.write(Map.of(
                        "reason", command.reason() == null ? "manual_release" : command.reason(),
                        "metadata", command.metadata() == null ? Map.of() : command.metadata(),
                        "referenceId", referenceId
                ))
        );

        return collateralRepository.findById(collateral.id())
                .orElseThrow(() -> new IllegalArgumentException("collateral not found after release: " + collateralId));
    }

    public record CreateCollateralCommand(
            long userId,
            String deviceId,
            BigDecimal amount,
            String assetCode,
            String initialStateRoot,
            Integer policyVersion,
            Map<String, Object> metadata
    ) {}

    public record ReleaseCollateralCommand(
            long userId,
            String deviceId,
            String reason,
            Map<String, Object> metadata
    ) {}
}
