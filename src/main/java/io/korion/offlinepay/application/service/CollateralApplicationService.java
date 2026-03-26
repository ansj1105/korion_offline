package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.status.OfflineSagaType;
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
    private final FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort;
    private final SettlementBatchEventBus settlementBatchEventBus;
    private final OfflineSagaService offlineSagaService;
    private final JsonService jsonService;
    private final AppProperties properties;

    public CollateralApplicationService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            CollateralOperationRepository collateralOperationRepository,
            FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort,
            SettlementBatchEventBus settlementBatchEventBus,
            OfflineSagaService offlineSagaService,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.collateralOperationRepository = collateralOperationRepository;
        this.foxCoinWalletSnapshotPort = foxCoinWalletSnapshotPort;
        this.settlementBatchEventBus = settlementBatchEventBus;
        this.offlineSagaService = offlineSagaService;
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
        CollateralLock aggregate = collateralRepository.findAggregateByUserIdAndAssetCode(
                        command.userId(),
                        assetCode
                )
                .orElse(null);
        FoxCoinWalletSnapshotPort.WalletSnapshot walletSnapshot =
                foxCoinWalletSnapshotPort.getCanonicalWalletSnapshot(command.userId(), assetCode);
        BigDecimal currentCollateralAmount = aggregate == null
                ? BigDecimal.ZERO
                : aggregate.lockedAmount().max(BigDecimal.ZERO);
        BigDecimal additionalCollateralAvailableAmount = walletSnapshot.totalBalance()
                .subtract(currentCollateralAmount)
                .max(BigDecimal.ZERO);
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new IllegalArgumentException("collateral amount is required");
        }
        if (command.amount().compareTo(additionalCollateralAvailableAmount) > 0) {
            throw new IllegalArgumentException("collateral amount exceeds foxya canonical snapshot balance");
        }
        int policyVersion = command.policyVersion() == null ? 1 : command.policyVersion();
        String initialStateRoot = command.initialStateRoot() == null || command.initialStateRoot().isBlank()
                ? "GENESIS"
                : command.initialStateRoot();
        String referenceId = buildTopupReferenceId(command);
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
        offlineSagaService.start(
                OfflineSagaType.COLLATERAL_TOPUP,
                operation.id(),
                "SERVER_ACCEPTED",
                Map.of(
                        "operationId", operation.id(),
                        "assetCode", operation.assetCode(),
                        "referenceId", operation.referenceId(),
                        "amount", operation.amount().toPlainString()
                )
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
        CollateralLock aggregate = collateralRepository.findAggregateByUserIdAndAssetCode(
                        collateral.userId(),
                        collateral.assetCode()
                )
                .orElseThrow(() -> new IllegalArgumentException("collateral remaining amount is empty: " + collateralId));

        if (collateral.userId() != command.userId()) {
            throw new IllegalArgumentException("collateral user mismatch: " + collateralId);
        }
        if (collateral.status() == CollateralStatus.RELEASED) {
            throw new IllegalArgumentException("collateral already released: " + collateralId);
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new IllegalArgumentException("release amount is required");
        }
        if (command.amount().compareTo(aggregate.remainingAmount()) > 0) {
            throw new IllegalArgumentException("release amount exceeds remaining collateral");
        }

        String referenceId = buildReleaseReferenceId(collateralId, command);
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
        offlineSagaService.start(
                OfflineSagaType.COLLATERAL_RELEASE,
                operation.id(),
                "SERVER_ACCEPTED",
                Map.of(
                        "operationId", operation.id(),
                        "assetCode", operation.assetCode(),
                        "referenceId", operation.referenceId(),
                        "amount", operation.amount().toPlainString()
                )
        );
        return operation;
    }

    private String buildTopupReferenceId(CreateCollateralCommand command) {
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        if (idempotencyKey != null) {
            return "topup:" + command.deviceId() + ":" + idempotencyKey;
        }
        return "topup:" + command.deviceId() + ":" + System.currentTimeMillis();
    }

    private String buildReleaseReferenceId(String collateralId, ReleaseCollateralCommand command) {
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        if (idempotencyKey != null) {
            return "release:" + collateralId + ":" + idempotencyKey;
        }
        return "release:" + collateralId + ":" + System.currentTimeMillis();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        return normalized.isBlank() ? null : normalized;
    }

    public record CreateCollateralCommand(
            long userId,
            String deviceId,
            BigDecimal amount,
            String assetCode,
            String initialStateRoot,
            Integer policyVersion,
            Map<String, Object> metadata,
            String idempotencyKey
    ) {}

    public record ReleaseCollateralCommand(
            long userId,
            String deviceId,
            BigDecimal amount,
            String reason,
            Map<String, Object> metadata,
            String idempotencyKey
    ) {}
}
