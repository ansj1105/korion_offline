package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollateralApplicationService {

    private static final int MAX_REFERENCE_SCOPE_LENGTH = 24;
    private static final int HASH_LENGTH = 16;

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final CollateralOperationRepository collateralOperationRepository;
    private final CoinManageCollateralPort coinManageCollateralPort;
    private final FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort;
    private final SettlementBatchEventBus settlementBatchEventBus;
    private final OfflineSagaService offlineSagaService;
    private final JsonService jsonService;
    private final AppProperties properties;

    public CollateralApplicationService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            CollateralOperationRepository collateralOperationRepository,
            CoinManageCollateralPort coinManageCollateralPort,
            FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort,
            SettlementBatchEventBus settlementBatchEventBus,
            OfflineSagaService offlineSagaService,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.collateralOperationRepository = collateralOperationRepository;
        this.coinManageCollateralPort = coinManageCollateralPort;
        this.foxCoinWalletSnapshotPort = foxCoinWalletSnapshotPort;
        this.settlementBatchEventBus = settlementBatchEventBus;
        this.offlineSagaService = offlineSagaService;
        this.jsonService = jsonService;
        this.properties = properties;
    }

    @Transactional
    public CollateralOperation createCollateral(CreateCollateralCommand command) {
        assertActiveSecurityDevice(command.userId(), command.deviceId());

        String assetCode = command.assetCode() == null || command.assetCode().isBlank()
                ? properties.assetCode()
                : command.assetCode();
        CollateralLock aggregate = collateralRepository.findAggregateByUserIdAndAssetCode(
                        command.userId(),
                        assetCode
                )
                .orElse(null);
        BigDecimal currentCollateralAmount = aggregate == null
                ? BigDecimal.ZERO
                : aggregate.lockedAmount().max(BigDecimal.ZERO);
        BigDecimal additionalCollateralAvailableAmount =
                resolveAdditionalCollateralAvailableAmount(command.userId(), assetCode, currentCollateralAmount);
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new IllegalArgumentException("collateral amount is required");
        }
        if (command.amount().compareTo(additionalCollateralAvailableAmount) > 0) {
            throw new IllegalArgumentException("collateral amount exceeds coin_manage available balance");
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

    private BigDecimal resolveAdditionalCollateralAvailableAmount(
            long userId,
            String assetCode,
            BigDecimal currentCollateralAmount
    ) {
        CoinManageCollateralPort.BalanceSnapshot balanceSnapshot =
                coinManageCollateralPort.getBalanceSnapshot(userId, assetCode);
        FoxCoinWalletSnapshotPort.WalletSnapshot walletSnapshot = balanceSnapshot.hasLedgerFootprint()
                ? null
                : foxCoinWalletSnapshotPort.getCanonicalWalletSnapshot(userId, assetCode);
        return CollateralLedgerAmountResolver.resolveAdditionalCollateralAvailableAmount(
                balanceSnapshot,
                walletSnapshot,
                currentCollateralAmount
        );
    }

    private BigDecimal resolveReleaseAvailableAmount(long userId, String assetCode, BigDecimal offlineRemainingAmount) {
        CoinManageCollateralPort.BalanceSnapshot balanceSnapshot =
                coinManageCollateralPort.getBalanceSnapshot(userId, assetCode);
        return CollateralLedgerAmountResolver.resolveSpendableCollateralAmount(
                balanceSnapshot,
                offlineRemainingAmount
        );
    }

    @Transactional(readOnly = true)
    public CollateralLock getCollateral(String collateralId) {
        return collateralRepository.findById(collateralId)
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + collateralId));
    }

    @Transactional(readOnly = true)
    public List<CollateralOperation> listCollateralOperations(long userId, String assetCode, Integer size) {
        int normalizedSize = size == null ? 50 : Math.min(Math.max(size, 1), 100);
        String normalizedAssetCode = assetCode == null || assetCode.isBlank()
                ? properties.assetCode()
                : assetCode.trim().toUpperCase();
        return collateralOperationRepository.findRecentByUserIdAndAssetCode(userId, normalizedAssetCode, normalizedSize);
    }

    @Transactional(readOnly = true)
    public CollateralOperation getCollateralOperation(String operationId, long userId) {
        CollateralOperation operation = collateralOperationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("collateral operation not found: " + operationId));
        if (operation.userId() != userId) {
            throw new IllegalArgumentException("collateral operation user mismatch: " + operationId);
        }
        return operation;
    }

    @Transactional
    public CollateralOperation releaseCollateral(String collateralId, ReleaseCollateralCommand command) {
        assertActiveSecurityDevice(command.userId(), command.deviceId());

        CollateralLock requestedCollateral = collateralRepository.findById(collateralId)
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + collateralId));
        CollateralLock aggregate = collateralRepository.findAggregateByUserIdAndAssetCode(
                        requestedCollateral.userId(),
                        requestedCollateral.assetCode()
                )
                .orElseThrow(() -> new IllegalArgumentException("collateral remaining amount is empty: " + collateralId));

        if (requestedCollateral.userId() != command.userId()) {
            throw new IllegalArgumentException("collateral user mismatch: " + collateralId);
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new IllegalArgumentException("release amount is required");
        }
        BigDecimal releaseAvailableAmount =
                resolveReleaseAvailableAmount(command.userId(), requestedCollateral.assetCode(), aggregate.remainingAmount());
        if (command.amount().compareTo(releaseAvailableAmount) > 0) {
            throw new IllegalArgumentException("release amount exceeds remaining collateral");
        }

        List<CollateralLock> activeLocks = collateralRepository.findActiveByUserIdAndAssetCode(
                command.userId(),
                requestedCollateral.assetCode()
        );
        CollateralLock releaseAnchor = activeLocks.stream()
                .filter(item -> item.id().equals(requestedCollateral.id()))
                .findFirst()
                .orElseGet(() -> activeLocks.stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("no releasable collateral found")));

        String referenceId = buildReleaseReferenceId(releaseAnchor.id(), command);
        CollateralOperation operation = collateralOperationRepository.saveRequested(
                releaseAnchor.id(),
                requestedCollateral.userId(),
                command.deviceId(),
                requestedCollateral.assetCode(),
                CollateralOperationType.RELEASE,
                command.amount(),
                referenceId,
                jsonService.write(Map.of(
                        "amount", command.amount(),
                        "reason", command.reason() == null ? "manual_release" : command.reason(),
                        "metadata", command.metadata() == null ? Map.of() : command.metadata(),
                        "requestedCollateralId", requestedCollateral.id(),
                        "releaseAnchorCollateralId", releaseAnchor.id()
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

    private void assertActiveSecurityDevice(long userId, String deviceId) {
        deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .filter(device -> device.status() == DeviceStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("active security device not registered: " + deviceId));
    }

    private String buildTopupReferenceId(CreateCollateralCommand command) {
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        return buildBoundedReferenceId("topup", command.deviceId(), idempotencyKey);
    }

    private String buildReleaseReferenceId(String collateralId, ReleaseCollateralCommand command) {
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        return buildBoundedReferenceId("release", collateralId, idempotencyKey);
    }

    private String buildBoundedReferenceId(String prefix, String scope, String idempotencyKey) {
        String compactScope = compactScope(scope);
        if (idempotencyKey != null) {
            return prefix + ":" + compactScope + ":" + fingerprint(idempotencyKey, HASH_LENGTH);
        }
        return prefix + ":" + compactScope + ":" + System.currentTimeMillis();
    }

    private String compactScope(String scope) {
        String normalized = scope == null ? "unknown" : scope.trim();
        if (normalized.length() <= MAX_REFERENCE_SCOPE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, 12) + "-" + fingerprint(normalized, 8);
    }

    private String fingerprint(String value, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.substring(0, Math.min(length, builder.length()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
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
