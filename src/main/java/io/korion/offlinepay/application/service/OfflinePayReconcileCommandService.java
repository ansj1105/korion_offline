package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflinePayReconcileCommandRepository;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePayReconcileCommand;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflinePayReconcileCommandStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflinePayReconcileCommandService {

    private static final long COMMAND_TTL_MINUTES = 15;
    private static final BigDecimal AMOUNT_GAP_THRESHOLD = new BigDecimal("0.000001");

    private final DeviceRepository deviceRepository;
    private final OfflineLedgerService offlineLedgerService;
    private final OfflinePayReconcileCommandRepository commandRepository;
    private final ProofIssuerSignatureService signatureService;
    private final Clock clock;

    @Autowired
    public OfflinePayReconcileCommandService(
            DeviceRepository deviceRepository,
            OfflineLedgerService offlineLedgerService,
            OfflinePayReconcileCommandRepository commandRepository,
            ProofIssuerSignatureService signatureService
    ) {
        this(deviceRepository, offlineLedgerService, commandRepository, signatureService, Clock.systemUTC());
    }

    OfflinePayReconcileCommandService(
            DeviceRepository deviceRepository,
            OfflineLedgerService offlineLedgerService,
            OfflinePayReconcileCommandRepository commandRepository,
            ProofIssuerSignatureService signatureService,
            Clock clock
    ) {
        this.deviceRepository = deviceRepository;
        this.offlineLedgerService = offlineLedgerService;
        this.commandRepository = commandRepository;
        this.signatureService = signatureService;
        this.clock = clock;
    }

    @Transactional
    public PollResponse poll(PollCommand command) {
        Device device = resolveActiveDevice(command.deviceId());
        String assetCode = normalizeAssetCode(command.assetCode());
        OffsetDateTime now = OffsetDateTime.now(clock);
        OfflineLedgerService.HubSummaryResponse serverSummary = offlineLedgerService.getHubSummary(device.deviceId(), assetCode);
        String projectionVersion = buildProjectionVersion(serverSummary);
        OfflinePayReconcileCommand existing = commandRepository
                .findRunnableByUserIdAndAssetCode(device.userId(), assetCode, now)
                .orElse(null);
        OfflinePayReconcileCommand selected = existing;
        String reasonCode = detectReasonCode(command.localSummary(), serverSummary);
        if (selected == null && reasonCode != null) {
            selected = commandRepository.create(
                    device.userId(),
                    assetCode,
                    reasonCode,
                    projectionVersion,
                    UUID.randomUUID().toString(),
                    now.plusMinutes(COMMAND_TTL_MINUTES),
                    Map.of(
                            "source", "APP_LOCAL_SUMMARY",
                            "deviceId", device.deviceId(),
                            "serverSummary", serverSummary,
                            "localSummary", command.localSummary() == null ? Map.of() : command.localSummary()
                    )
            );
        }
        if (selected == null) {
            return new PollResponse(
                    device.deviceId(),
                    device.userId(),
                    assetCode,
                    false,
                    "NO_RECONCILE_REQUIRED",
                    null
            );
        }
        OfflinePayReconcileCommand delivered = commandRepository.markDelivered(selected.id(), device.deviceId());
        return new PollResponse(
                device.deviceId(),
                device.userId(),
                assetCode,
                true,
                "RECONCILE_COMMAND_AVAILABLE",
                toSignedCommand(delivered)
        );
    }

    @Transactional
    public ReportResponse report(ReportCommand command) {
        Device device = resolveActiveDevice(command.deviceId());
        OfflinePayReconcileCommand existing = commandRepository
                .findByIdAndNonce(command.commandId(), command.nonce())
                .orElseThrow(() -> new IllegalArgumentException("reconcile command not found"));
        if (existing.userId() != device.userId()) {
            throw new IllegalArgumentException("reconcile command user mismatch");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (existing.expiresAt().isBefore(now)) {
            commandRepository.markReported(
                    existing.id(),
                    OfflinePayReconcileCommandStatus.EXPIRED,
                    device.deviceId(),
                    command.dryRunSummary(),
                    command.applySummary(),
                    command.localSummary(),
                    "command expired"
            );
            throw new IllegalArgumentException("reconcile command expired");
        }
        OfflinePayReconcileCommandStatus status = normalizeReportStatus(command.status());
        OfflinePayReconcileCommand updated = commandRepository.markReported(
                existing.id(),
                status,
                device.deviceId(),
                command.dryRunSummary(),
                command.applySummary(),
                command.localSummary(),
                command.errorMessage()
        );
        return new ReportResponse(
                updated.id(),
                device.deviceId(),
                updated.userId(),
                updated.assetCode(),
                updated.status().name(),
                updated.updatedAt().toString()
        );
    }

    private SignedCommand toSignedCommand(OfflinePayReconcileCommand command) {
        String signingPayload = buildSigningPayload(command);
        return new SignedCommand(
                command.id(),
                command.userId(),
                command.assetCode(),
                command.reasonCode(),
                command.projectionVersion(),
                command.expiresAt().toString(),
                command.nonce(),
                command.status().name(),
                signingPayload,
                signatureService.sign(signingPayload),
                signatureService.keyId(),
                signatureService.publicKey(),
                command.createdAt().toString()
        );
    }

    private Device resolveActiveDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
        Device device = deviceRepository.findByDeviceId(deviceId.trim())
                .orElseThrow(() -> new IllegalArgumentException("device not registered: " + deviceId));
        if (device.status() != DeviceStatus.ACTIVE) {
            throw new IllegalArgumentException("device is not active: " + deviceId);
        }
        return device;
    }

    private String detectReasonCode(Map<String, Object> localSummary, OfflineLedgerService.HubSummaryResponse serverSummary) {
        if (localSummary == null || localSummary.isEmpty()) {
            return null;
        }
        int localOutboxPending = intValue(localSummary.get("outboxPendingCount"));
        if (localOutboxPending > 0) {
            return "LOCAL_OUTBOX_PENDING";
        }
        int localPending = intValue(localSummary.get("pendingCount"));
        if (localPending > serverSummary.pendingCount()) {
            return "LOCAL_PENDING_SERVER_PROJECTION_GAP";
        }
        BigDecimal localUnsettled = amountValue(localSummary.get("unsettledReceivedAmount"));
        if (amountGap(localUnsettled, new BigDecimal(serverSummary.unsettledReceivedAmount()))) {
            return "LOCAL_UNSETTLED_SERVER_PROJECTION_GAP";
        }
        BigDecimal localAvailable = firstAmount(localSummary, "offlineAvailableAmount", "effectiveAvailableAmount", "availableAmount");
        if (localAvailable != null && amountGap(localAvailable, new BigDecimal(serverSummary.offlineAvailableAmount()))) {
            return "LOCAL_COLLATERAL_SERVER_PROJECTION_GAP";
        }
        return null;
    }

    private String buildProjectionVersion(OfflineLedgerService.HubSummaryResponse summary) {
        return "hub-summary:" + summary.assetCode() + ":" + summary.refreshedAt();
    }

    private String buildSigningPayload(OfflinePayReconcileCommand command) {
        return String.join(
                "|",
                "RECONCILE_COMMAND_V1",
                command.id(),
                String.valueOf(command.userId()),
                command.assetCode(),
                command.reasonCode(),
                command.projectionVersion(),
                command.expiresAt().toString(),
                command.nonce()
        );
    }

    private OfflinePayReconcileCommandStatus normalizeReportStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return switch (normalized) {
            case "APPLIED" -> OfflinePayReconcileCommandStatus.APPLIED;
            case "FAILED" -> OfflinePayReconcileCommandStatus.FAILED;
            default -> throw new IllegalArgumentException("unsupported reconcile report status: " + status);
        };
    }

    private String normalizeAssetCode(String assetCode) {
        return assetCode == null || assetCode.isBlank() ? "KORI" : assetCode.trim().toUpperCase();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value == null ? "0" : value).trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private BigDecimal amountValue(Object value) {
        try {
            return new BigDecimal(String.valueOf(value == null ? "0" : value).replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal firstAmount(Map<String, Object> summary, String... keys) {
        for (String key : keys) {
            if (summary.containsKey(key)) {
                return amountValue(summary.get(key));
            }
        }
        return null;
    }

    private boolean amountGap(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(AMOUNT_GAP_THRESHOLD) > 0;
    }

    public record PollCommand(
            String deviceId,
            String assetCode,
            Map<String, Object> localSummary
    ) {}

    public record PollResponse(
            String deviceId,
            long userId,
            String assetCode,
            boolean hasCommand,
            String reasonCode,
            SignedCommand command
    ) {}

    public record SignedCommand(
            String id,
            long userId,
            String assetCode,
            String reasonCode,
            String projectionVersion,
            String expiresAt,
            String nonce,
            String status,
            String signingPayload,
            String signature,
            String signingKeyId,
            String signingPublicKey,
            String createdAt
    ) {}

    public record ReportCommand(
            String deviceId,
            String commandId,
            String nonce,
            String status,
            Map<String, Object> dryRunSummary,
            Map<String, Object> applySummary,
            Map<String, Object> localSummary,
            String errorMessage
    ) {}

    public record ReportResponse(
            String commandId,
            String deviceId,
            long userId,
            String assetCode,
            String status,
            String updatedAt
    ) {}
}
