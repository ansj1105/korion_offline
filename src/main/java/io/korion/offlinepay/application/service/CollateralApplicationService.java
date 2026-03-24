package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.CollateralStatus;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollateralApplicationService {

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final CollateralOperationRepository collateralOperationRepository;
    private final SettlementBatchEventBus settlementBatchEventBus;
    private final JsonService jsonService;
    private final AppProperties properties;

    public CollateralApplicationService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            CollateralOperationRepository collateralOperationRepository,
            SettlementBatchEventBus settlementBatchEventBus,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.collateralOperationRepository = collateralOperationRepository;
        this.settlementBatchEventBus = settlementBatchEventBus;
        this.jsonService = jsonService;
        this.properties = properties;
    }

    @Transactional
    public CollateralOperation createCollateral(CreateCollateralCommand command) {
        deviceRepository.findByDeviceId(command.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("device not registered: " + command.deviceId()));

        String assetCode = command.assetCode() == null || command.assetCode().isBlank()
                ? properties.assetCode()
                : command.assetCode();
        int policyVersion = command.policyVersion() == null ? 1 : command.policyVersion();
        String initialStateRoot = command.initialStateRoot() == null || command.initialStateRoot().isBlank()
                ? "GENESIS"
                : command.initialStateRoot();
        String referenceId = "topup:" + command.deviceId() + ":" + System.currentTimeMillis();
        CollateralOperation operation = collateralOperationRepository.saveRequested(
                null,
                command.userId(),
                command.deviceId(),
                assetCode,
                CollateralOperationType.TOPUP,
                command.amount(),
                referenceId,
                jsonService.write(Map.of(
                        "policyVersion", policyVersion,
                        "initialStateRoot", initialStateRoot,
                        "metadata", command.metadata() == null ? Map.of() : command.metadata()
                ))
        );
        settlementBatchEventBus.publishCollateralOperationRequested(
                operation.id(),
                operation.operationType().name(),
                operation.assetCode(),
                operation.referenceId(),
                operation.createdAt().toString()
        );
        return operation;
    }

    @Transactional(readOnly = true)
    public CollateralLock getCollateral(String collateralId) {
        return collateralRepository.findById(collateralId)
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + collateralId));
    }

    @Transactional
    public CollateralOperation releaseCollateral(String collateralId, ReleaseCollateralCommand command) {
        CollateralLock collateral = collateralRepository.findById(collateralId)
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + collateralId));
        CollateralLock aggregate = collateralRepository.findAggregateByUserIdAndDeviceIdAndAssetCode(
                        collateral.userId(),
                        collateral.deviceId(),
                        collateral.assetCode()
                )
                .orElseThrow(() -> new IllegalArgumentException("collateral remaining amount is empty: " + collateralId));

        if (collateral.userId() != command.userId()) {
            throw new IllegalArgumentException("collateral user mismatch: " + collateralId);
        }
        if (!collateral.deviceId().equals(command.deviceId())) {
            throw new IllegalArgumentException("collateral device mismatch: " + collateralId);
        }
        if (collateral.status() == CollateralStatus.RELEASED) {
            throw new IllegalArgumentException("collateral already released: " + collateralId);
        }
        if (collateral.remainingAmount().signum() <= 0) {
            throw new IllegalArgumentException("collateral remaining amount is empty: " + collateralId);
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new IllegalArgumentException("release amount is required");
        }
        if (command.amount().compareTo(aggregate.remainingAmount()) > 0) {
            throw new IllegalArgumentException("release amount exceeds remaining collateral");
        }

        String referenceId = "release:" + collateralId;
        CollateralOperation operation = collateralOperationRepository.saveRequested(
                collateral.id(),
                collateral.userId(),
                collateral.deviceId(),
                collateral.assetCode(),
                CollateralOperationType.RELEASE,
                command.amount(),
                referenceId,
                jsonService.write(Map.of(
                        "amount", command.amount(),
                        "reason", command.reason() == null ? "manual_release" : command.reason(),
                        "metadata", command.metadata() == null ? Map.of() : command.metadata()
                ))
        );
        settlementBatchEventBus.publishCollateralOperationRequested(
                operation.id(),
                operation.operationType().name(),
                operation.assetCode(),
                operation.referenceId(),
                operation.createdAt().toString()
        );
        return operation;
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
            BigDecimal amount,
            String reason,
            Map<String, Object> metadata
    ) {}
}
