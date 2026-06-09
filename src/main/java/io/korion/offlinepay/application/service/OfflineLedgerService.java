package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflineLedgerService {

    private static final Set<String> RECEIVER_SETTLEMENT_PENDING_REASONS = Set.of(
            OfflinePayReasonCode.COUNTER_GAP,
            OfflinePayReasonCode.SEND_INTERRUPTED,
            OfflinePayReasonCode.SEND_TIMEOUT,
            OfflinePayReasonCode.BATCH_SYNC_FAIL,
            OfflinePayReasonCode.HISTORY_SYNC_FAIL,
            OfflinePayReasonCode.HISTORY_CIRCUIT_OPEN,
            "BLE_ACK_TIMEOUT",
            "BLE_SEND_TIMEOUT",
            "BLE_SEND_STOPPED",
            "SESSION_CLEANUP"
    );

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final CollateralOperationRepository collateralOperationRepository;
    private final OfflinePaymentProofRepository offlinePaymentProofRepository;
    private final OfflinePayDeviceIdentifierResolver deviceIdentifierResolver;
    private final AppProperties properties;
    private final JsonService jsonService;

    public OfflineLedgerService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            CollateralOperationRepository collateralOperationRepository,
            OfflinePaymentProofRepository offlinePaymentProofRepository,
            OfflinePayDeviceIdentifierResolver deviceIdentifierResolver,
            AppProperties properties,
            JsonService jsonService
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.collateralOperationRepository = collateralOperationRepository;
        this.offlinePaymentProofRepository = offlinePaymentProofRepository;
        this.deviceIdentifierResolver = deviceIdentifierResolver;
        this.properties = properties;
        this.jsonService = jsonService;
    }

    @Transactional(readOnly = true)
    public LedgerHistoryResponse getLedgerHistory(long userId, String assetCode, Integer size) {
        String normalizedAssetCode = assetCode == null || assetCode.isBlank()
                ? properties.assetCode()
                : assetCode.trim().toUpperCase();
        int normalizedSize = size == null || size <= 0 ? 200 : Math.min(size, 500);
        Set<String> activeDeviceIds = loadActiveDeviceIds(userId);
        List<CollateralOperation> operations = collateralOperationRepository
                .findRecentByUserIdAndAssetCode(userId, normalizedAssetCode, normalizedSize);
        List<OfflinePaymentProof> proofs = offlinePaymentProofRepository
                .findRecentByUserIdAndAssetCode(userId, normalizedAssetCode, normalizedSize);
        BigDecimal currentRemainingAmount = collateralRepository
                .findAggregateByUserIdAndAssetCode(userId, normalizedAssetCode)
                .map(CollateralLock::remainingAmount)
                .orElse(BigDecimal.ZERO)
                .max(BigDecimal.ZERO);

        List<LedgerEvent> sentEvents = new ArrayList<>();
        List<LedgerEvent> receivedEvents = new ArrayList<>();

        for (CollateralOperation operation : operations) {
            sentEvents.add(toCollateralEvent(operation));
        }

        for (OfflinePaymentProof proof : proofs) {
            LedgerEvent event = toProofEvent(proof, userId, activeDeviceIds);
            if (event == null) {
                continue;
            }
            if (event.direction() == LedgerDirection.RECEIVE) {
                receivedEvents.add(event);
            } else {
                sentEvents.add(event);
            }
        }

        sentEvents.sort(Comparator.comparing(LedgerEvent::time).reversed());
        receivedEvents.sort(Comparator.comparing(LedgerEvent::time).reversed());

        List<LedgerHistoryItem> sentItems = buildSentItems(sentEvents, currentRemainingAmount);
        List<LedgerHistoryItem> receivedItems = buildReceivedItems(receivedEvents);

        return new LedgerHistoryResponse(
                normalizedAssetCode,
                sentItems,
                receivedItems,
                receivedItems.stream()
                        .filter(item -> !"FAILED".equals(item.statusCode()))
                        .map(item -> parseAmount(item.amount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .toPlainString(),
                OffsetDateTime.now().toString()
        );
    }

    private Set<String> loadActiveDeviceIds(long userId) {
        Set<String> deviceIds = new HashSet<>();
        for (Device device : deviceRepository.findActiveByUserId(userId)) {
            if (device != null && device.deviceId() != null && !device.deviceId().isBlank()) {
                deviceIds.add(device.deviceId());
            }
        }
        return deviceIds;
    }

    private List<LedgerHistoryItem> buildSentItems(List<LedgerEvent> events, BigDecimal currentRemainingAmount) {
        List<LedgerHistoryItem> items = new ArrayList<>();
        BigDecimal runningRemaining = currentRemainingAmount.max(BigDecimal.ZERO);
        for (LedgerEvent event : events) {
            BigDecimal balanceAfterEvent = runningRemaining.max(BigDecimal.ZERO);
            String formattedAmount = (event.isTopup() ? "+" : "-") + event.amount().toPlainString();
            items.add(new LedgerHistoryItem(
                    event.id(),
                    event.date(),
                    event.title(),
                    event.memo(),
                    formattedAmount,
                    null,
                    balanceAfterEvent.toPlainString(),
                    event.statusLabel(),
                    event.statusCode(),
                    event.network(),
                    event.assetCode(),
                    event.walletAddress(),
                    event.time().toString(),
                    event.transactionType(),
                    event.transactionScope(),
                    event.detailType(),
                    event.counterpartyName(),
                    event.fee(),
                    event.category(),
                    event.paymentMethod(),
                    event.unsettledAmount().toPlainString(),
                    event.settledAmount().toPlainString()
            ));

            if (!event.affectsServerBalance()) {
                continue;
            }

            if (event.isTopup()) {
                runningRemaining = runningRemaining.subtract(event.amount()).max(BigDecimal.ZERO);
            } else {
                runningRemaining = runningRemaining.add(event.amount());
            }
        }
        return items;
    }

    private List<LedgerHistoryItem> buildReceivedItems(List<LedgerEvent> events) {
        List<LedgerHistoryItem> items = new ArrayList<>();
        BigDecimal runningReceived = BigDecimal.ZERO;
        for (LedgerEvent event : events) {
            if (event.affectsServerBalance()) {
                runningReceived = runningReceived.add(event.amount());
            }
        }

        for (LedgerEvent event : events) {
            BigDecimal cumulativeAmount = runningReceived.max(BigDecimal.ZERO);
            items.add(new LedgerHistoryItem(
                    event.id(),
                    event.date(),
                    event.title(),
                    event.memo(),
                    "+" + event.amount().toPlainString(),
                    cumulativeAmount.toPlainString(),
                    cumulativeAmount.toPlainString(),
                    event.statusLabel(),
                    event.statusCode(),
                    event.network(),
                    event.assetCode(),
                    event.walletAddress(),
                    event.time().toString(),
                    event.transactionType(),
                    event.transactionScope(),
                    event.detailType(),
                    event.counterpartyName(),
                    event.fee(),
                    event.category(),
                    event.paymentMethod(),
                    event.unsettledAmount().toPlainString(),
                    event.settledAmount().toPlainString()
            ));
            if (event.affectsServerBalance()) {
                runningReceived = runningReceived.subtract(event.amount()).max(BigDecimal.ZERO);
            }
        }
        return items;
    }

    private LedgerEvent toCollateralEvent(CollateralOperation operation) {
        boolean completed = operation.status() == CollateralOperationStatus.COMPLETED;
        boolean failed = operation.status() == CollateralOperationStatus.FAILED;
        String statusCode = failed ? "FAILED" : completed ? "COMPLETED" : "PENDING";
        String title = operation.operationType().name().equals("RELEASE") ? "담보해제" : "담보충전";
        String memo = readMetadataText(operation.metadataJson(), "description");
        if (memo.isBlank()) {
            memo = operation.operationType().name().equals("RELEASE") ? "오프라인 담보 해제 요청" : "오프라인 담보 충전 요청";
        }

        return new LedgerEvent(
                "collateral:" + operation.id(),
                LedgerDirection.SEND,
                title,
                memo,
                operation.amount().abs(),
                statusCode,
                statusLabel(statusCode),
                "mainnet",
                operation.assetCode(),
                operation.deviceId(),
                operation.updatedAt() != null ? operation.updatedAt() : operation.createdAt(),
                operation.operationType().name().equals("RELEASE") ? "Collateral Release" : "Collateral Charge",
                "Online",
                operation.operationType().name().equals("RELEASE") ? "담보해제" : "담보충전",
                "본인지갑",
                "0.000000",
                "COLLATERAL",
                "SYSTEM",
                completed,
                operation.operationType().name().equals("TOPUP"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private LedgerEvent toProofEvent(OfflinePaymentProof proof, long userId, Set<String> activeDeviceIds) {
        Device senderDevice = deviceIdentifierResolver.resolve(proof.senderDeviceId()).orElse(null);
        Device receiverDevice = deviceIdentifierResolver.resolve(proof.receiverDeviceId()).orElse(null);
        boolean senderOwned = activeDeviceIds.contains(proof.senderDeviceId())
                || (senderDevice != null && senderDevice.userId() == userId);
        boolean receiverOwned = activeDeviceIds.contains(proof.receiverDeviceId())
                || (receiverDevice != null && receiverDevice.userId() == userId);
        if (!senderOwned && !receiverOwned) {
            return null;
        }
        String senderDeviceId = senderDevice == null ? proof.senderDeviceId() : senderDevice.deviceId();
        String receiverDeviceId = receiverDevice == null ? proof.receiverDeviceId() : receiverDevice.deviceId();

        JsonNode payload = jsonService.readTree(proof.rawPayloadJson());
        String counterparty = firstNonBlank(
                payload.path("counterparty").asText(""),
                payload.path("actor").asText(""),
                senderOwned ? receiverDeviceId : senderDeviceId
        );
        String description = firstNonBlank(payload.path("description").asText(""), senderOwned ? "오프라인 전송 요청" : "오프라인 수취 요청");
        String category = normalizeCategory(payload.path("category").asText(""));
        String paymentMethod = resolvePaymentMethod(payload, proof.channelType());
        String network = firstNonBlank(payload.path("network").asText(""), "mainnet");
        LedgerDirection direction = senderOwned ? LedgerDirection.SEND : LedgerDirection.RECEIVE;
        boolean completed = proof.status() == OfflineProofStatus.SETTLED;
        boolean failed = switch (proof.status()) {
            case FAILED, CONFLICTED, REJECTED, EXPIRED -> true;
            default -> false;
        };
        if (direction == LedgerDirection.RECEIVE && failed && shouldKeepReceivedProofPending(proof)) {
            failed = false;
        }
        String statusCode = failed ? "FAILED" : completed ? "COMPLETED" : "PENDING";
        BigDecimal receivedUnsettledAmount = senderOwned
                ? BigDecimal.ZERO
                : resolveReceivedUnsettledAmount(proof, statusCode);

        return new LedgerEvent(
                "proof:" + proof.id(),
                direction,
                counterparty,
                description,
                proof.amount().abs(),
                statusCode,
                statusLabel(statusCode),
                network,
                payload.path("token").asText(firstNonBlank(payload.path("assetCode").asText(""), "KORI")),
                senderOwned ? receiverDeviceId : senderDeviceId,
                resolveProofTime(proof),
                senderOwned ? "Offline Send" : "Offline Receive",
                "Offline",
                resolveDetailType(category),
                counterparty,
                normalizeDecimalText(payload.path("fee").asText("0.000000")),
                category,
                paymentMethod,
                completed,
                false,
                receivedUnsettledAmount,
                senderOwned ? BigDecimal.ZERO : normalizeAmount(proof.receivedSettledAmount())
        );
    }

    private boolean shouldKeepReceivedProofPending(OfflinePaymentProof proof) {
        String reasonCode = proof.reasonCode() == null ? "" : proof.reasonCode().trim().toUpperCase();
        return RECEIVER_SETTLEMENT_PENDING_REASONS.contains(reasonCode);
    }

    private BigDecimal resolveReceivedUnsettledAmount(OfflinePaymentProof proof, String statusCode) {
        BigDecimal unsettledAmount = normalizeAmount(proof.receivedUnsettledAmount());
        if (!"PENDING".equals(statusCode) || unsettledAmount.compareTo(BigDecimal.ZERO) > 0) {
            return unsettledAmount;
        }
        BigDecimal settledAmount = normalizeAmount(proof.receivedSettledAmount());
        if (settledAmount.compareTo(BigDecimal.ZERO) > 0) {
            return unsettledAmount;
        }
        return normalizeAmount(proof.amount());
    }

    private OffsetDateTime resolveProofTime(OfflinePaymentProof proof) {
        if (proof.settledAt() != null) {
            return proof.settledAt();
        }
        if (proof.updatedAt() != null) {
            return proof.updatedAt();
        }
        if (proof.uploadedAt() != null) {
            return proof.uploadedAt();
        }
        return proof.createdAt();
    }

    private String resolveDetailType(String category) {
        return switch (category) {
            case "STORE" -> "스토어";
            case "SETTLEMENT" -> "정산";
            case "COLLATERAL" -> "담보";
            default -> "일반";
        };
    }

    private String resolvePaymentMethod(JsonNode payload, String channelType) {
        String paymentMethod = payload.path("paymentMethod").asText("");
        if (!paymentMethod.isBlank()) {
            return paymentMethod.trim().toUpperCase();
        }
        if (channelType != null && !channelType.isBlank() && !"UNKNOWN".equalsIgnoreCase(channelType)) {
            return channelType.trim().toUpperCase();
        }
        String connectionType = payload.path("connectionType").asText("");
        if ("MANUAL_SELECTION".equalsIgnoreCase(connectionType)) {
            return "BLE";
        }
        return "NFC";
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase();
        return switch (normalized) {
            case "STORE", "SETTLEMENT", "COLLATERAL" -> normalized;
            default -> "P2P";
        };
    }

    private String normalizeDecimalText(String value) {
        if (value == null || value.isBlank()) {
            return "0.000000";
        }
        try {
            return new BigDecimal(value.trim()).max(BigDecimal.ZERO).toPlainString();
        } catch (NumberFormatException ignored) {
            return "0.000000";
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
    }

    private BigDecimal parseAmount(String formattedAmount) {
        if (formattedAmount == null || formattedAmount.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = formattedAmount.startsWith("+") || formattedAmount.startsWith("-")
                ? formattedAmount.substring(1)
                : formattedAmount;
        try {
            return new BigDecimal(normalized.trim()).abs();
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String readMetadataText(String metadataJson, String fieldName) {
        try {
            JsonNode root = jsonService.readTree(metadataJson);
            JsonNode node = root.path(fieldName);
            if (node.isMissingNode() || node.isNull()) {
                return "";
            }
            return node.asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String statusLabel(String statusCode) {
        return switch (statusCode) {
            case "COMPLETED" -> "완료";
            case "FAILED" -> "실패";
            default -> "대기";
        };
    }

    public record LedgerHistoryResponse(
            String assetCode,
            List<LedgerHistoryItem> sentItems,
            List<LedgerHistoryItem> receivedItems,
            String totalReceivedAmount,
            String refreshedAt
    ) {}

    public record LedgerHistoryItem(
            String id,
            String date,
            String title,
            String memo,
            String amount,
            String subAmount,
            String balance,
            String status,
            String statusCode,
            String network,
            String token,
            String walletAddress,
            String time,
            String transactionType,
            String transactionScope,
            String detailType,
            String counterpartyName,
            String fee,
            String category,
            String paymentMethod,
            String unsettledAmount,
            String settledAmount
    ) {}

    private record LedgerEvent(
            String id,
            LedgerDirection direction,
            String title,
            String memo,
            BigDecimal amount,
            String statusCode,
            String statusLabel,
            String network,
            String assetCode,
            String walletAddress,
            OffsetDateTime time,
            String transactionType,
            String transactionScope,
            String detailType,
            String counterpartyName,
            String fee,
            String category,
            String paymentMethod,
            boolean affectsServerBalance,
            boolean isTopup,
            BigDecimal unsettledAmount,
            BigDecimal settledAmount
    ) {
        String date() {
            return String.format("%02d.%02d", time.getMonthValue(), time.getDayOfMonth());
        }
    }

    private enum LedgerDirection {
        SEND,
        RECEIVE
    }
}
