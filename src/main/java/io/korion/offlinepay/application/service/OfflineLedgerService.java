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
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.application.service.settlement.OfflinePaySettlementFeeCalculator;
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

    private static final int LEDGER_HISTORY_DEFAULT_SIZE = 30;
    private static final int LEDGER_HISTORY_MAX_SIZE = 30;
    private static final int LEDGER_HISTORY_MAX_FETCH_SIZE = 300;

    private final DeviceRepository deviceRepository;
    private final CollateralRepository collateralRepository;
    private final CollateralOperationRepository collateralOperationRepository;
    private final OfflinePaymentProofRepository offlinePaymentProofRepository;
    private final OfflinePayDeviceIdentifierResolver deviceIdentifierResolver;
    private final AppProperties properties;
    private final JsonService jsonService;
    private final OfflinePaySettlementFeeCalculator feeCalculator;

    public OfflineLedgerService(
            DeviceRepository deviceRepository,
            CollateralRepository collateralRepository,
            CollateralOperationRepository collateralOperationRepository,
            OfflinePaymentProofRepository offlinePaymentProofRepository,
            OfflinePayDeviceIdentifierResolver deviceIdentifierResolver,
            AppProperties properties,
            JsonService jsonService,
            OfflinePaySettlementFeeCalculator feeCalculator
    ) {
        this.deviceRepository = deviceRepository;
        this.collateralRepository = collateralRepository;
        this.collateralOperationRepository = collateralOperationRepository;
        this.offlinePaymentProofRepository = offlinePaymentProofRepository;
        this.deviceIdentifierResolver = deviceIdentifierResolver;
        this.properties = properties;
        this.jsonService = jsonService;
        this.feeCalculator = feeCalculator;
    }

    @Transactional(readOnly = true)
    public LedgerHistoryResponse getLedgerHistory(long userId, String assetCode, Integer size) {
        return getLedgerHistory(userId, assetCode, size, 0);
    }

    @Transactional(readOnly = true)
    public LedgerHistoryResponse getLedgerHistory(long userId, String assetCode, Integer size, Integer page) {
        String normalizedAssetCode = assetCode == null || assetCode.isBlank()
                ? properties.assetCode()
                : assetCode.trim().toUpperCase();
        int normalizedSize = size == null || size <= 0
                ? LEDGER_HISTORY_DEFAULT_SIZE
                : Math.min(size, LEDGER_HISTORY_MAX_SIZE);
        int normalizedPage = page == null || page <= 0 ? 0 : page;
        long requestedOffset = (long) normalizedPage * normalizedSize;
        int offset = requestedOffset > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) requestedOffset;
        int fetchSize = Math.min(
                LEDGER_HISTORY_MAX_FETCH_SIZE,
                Math.max(normalizedSize + 1, offset + normalizedSize + 1)
        );
        Set<String> activeDeviceIds = loadActiveDeviceIds(userId);
        List<CollateralOperation> operations = collateralOperationRepository
                .findRecentByUserIdAndAssetCode(userId, normalizedAssetCode, fetchSize);
        List<OfflinePaymentProof> proofs = offlinePaymentProofRepository
                .findRecentByUserIdAndAssetCode(userId, normalizedAssetCode, fetchSize);
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
                LedgerEvent receivedSettlementEvent = toReceivedSettlementEvent(proof, event);
                if (receivedSettlementEvent != null) {
                    receivedEvents.add(receivedSettlementEvent);
                }
            } else {
                sentEvents.add(event);
            }
        }

        Comparator<LedgerEvent> ledgerOrder = Comparator
                .comparing(LedgerEvent::time)
                .thenComparingLong(LedgerEvent::offlineTxSequence)
                .reversed();
        sentEvents.sort(ledgerOrder);
        receivedEvents.sort(ledgerOrder);

        List<LedgerHistoryItem> allSentItems = buildSentItems(sentEvents, currentRemainingAmount);
        List<LedgerHistoryItem> allReceivedItems = buildReceivedItems(receivedEvents);
        List<LedgerHistoryItem> sentItems = sliceLedgerPage(allSentItems, offset, normalizedSize);
        List<LedgerHistoryItem> receivedItems = sliceLedgerPage(allReceivedItems, offset, normalizedSize);

        return new LedgerHistoryResponse(
                normalizedAssetCode,
                sentItems,
                receivedItems,
                calculateReceivedTotal(receivedItems).toPlainString(),
                OffsetDateTime.now().toString(),
                normalizedPage,
                normalizedSize,
                hasNextLedgerPage(allSentItems, offset, normalizedSize),
                hasNextLedgerPage(allReceivedItems, offset, normalizedSize)
        );
    }

    @Transactional(readOnly = true)
    public HubProjectionResponse getHubProjection(
            String deviceId,
            String tab,
            String assetCode,
            Integer limit,
            Integer page
    ) {
        Device device = resolveRegisteredDevice(deviceId);
        LedgerHistoryResponse history = getLedgerHistory(device.userId(), assetCode, limit, page);
        String normalizedTab = normalizeTab(tab);
        List<LedgerHistoryItem> items = "RECEIVED".equals(normalizedTab)
                ? history.receivedItems()
                : history.sentItems();
        boolean hasNext = "RECEIVED".equals(normalizedTab)
                ? history.receivedHasNext()
                : history.sentHasNext();
        return new HubProjectionResponse(
                device.deviceId(),
                device.userId(),
                history.assetCode(),
                normalizedTab,
                items,
                hasNext,
                history.totalReceivedAmount(),
                history.refreshedAt(),
                history.page(),
                history.size()
        );
    }

    @Transactional(readOnly = true)
    public HubSummaryResponse getHubSummary(String deviceId, String assetCode) {
        Device device = resolveRegisteredDevice(deviceId);
        String normalizedAssetCode = assetCode == null || assetCode.isBlank()
                ? properties.assetCode()
                : assetCode.trim().toUpperCase();
        Set<String> activeDeviceIds = loadActiveDeviceIds(device.userId());
        List<OfflinePaymentProof> proofs = offlinePaymentProofRepository
                .findRecentByUserIdAndAssetCode(device.userId(), normalizedAssetCode, LEDGER_HISTORY_MAX_FETCH_SIZE);
        List<CollateralOperation> operations = collateralOperationRepository
                .findRecentByUserIdAndAssetCode(device.userId(), normalizedAssetCode, LEDGER_HISTORY_MAX_FETCH_SIZE);
        CollateralLock collateral = collateralRepository
                .findAggregateByUserIdAndAssetCode(device.userId(), normalizedAssetCode)
                .orElse(null);

        BigDecimal unsettledReceivedAmount = BigDecimal.ZERO;
        int failedCount = 0;
        int pendingCount = 0;

        for (OfflinePaymentProof proof : proofs) {
            LedgerEvent event = toProofEvent(proof, device.userId(), activeDeviceIds);
            if (event == null) {
                continue;
            }
            if (event.statusCode() == PublicLedgerStatus.FAILED
                    || event.statusCode() == PublicLedgerStatus.EXPIRED
                    || event.statusCode() == PublicLedgerStatus.REJECTED) {
                failedCount++;
                continue;
            }
            if (event.statusCode() == PublicLedgerStatus.PENDING) {
                pendingCount++;
            }
            if (event.direction() == LedgerDirection.RECEIVE) {
                unsettledReceivedAmount = unsettledReceivedAmount.add(event.unsettledAmount());
            }
        }

        for (CollateralOperation operation : operations) {
            LedgerEvent event = toCollateralEvent(operation);
            if (event.statusCode() == PublicLedgerStatus.FAILED) {
                failedCount++;
            } else if (event.statusCode() == PublicLedgerStatus.PENDING) {
                pendingCount++;
            }
        }

        return new HubSummaryResponse(
                device.deviceId(),
                device.userId(),
                normalizedAssetCode,
                unsettledReceivedAmount.max(BigDecimal.ZERO).toPlainString(),
                collateral == null ? "0" : normalizeAmount(collateral.remainingAmount()).toPlainString(),
                collateral == null ? "0" : normalizeAmount(collateral.lockedAmount()).toPlainString(),
                failedCount,
                pendingCount,
                OffsetDateTime.now().toString()
        );
    }

    private Device resolveRegisteredDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
        return deviceRepository.findByDeviceId(deviceId.trim())
                .orElseThrow(() -> new IllegalArgumentException("device not registered: " + deviceId));
    }

    private String normalizeTab(String tab) {
        String normalized = tab == null ? "SENT" : tab.trim().toUpperCase();
        return "RECEIVED".equals(normalized) ? "RECEIVED" : "SENT";
    }

    private List<LedgerHistoryItem> sliceLedgerPage(List<LedgerHistoryItem> items, int offset, int size) {
        if (offset >= items.size()) {
            return List.of();
        }
        return items.subList(offset, Math.min(items.size(), offset + size));
    }

    private boolean hasNextLedgerPage(List<LedgerHistoryItem> items, int offset, int size) {
        return items.size() > offset + size;
    }

    private BigDecimal calculateReceivedTotal(List<LedgerHistoryItem> receivedItems) {
        return receivedItems.stream()
                .filter(item -> !PublicLedgerStatus.FAILED.name().equals(item.statusCode()))
                .map(item -> parseAmount(item.unsettledAmount()).add(parseAmount(item.settledAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                    event.statusCode().name(),
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
                    event.settledAmount().toPlainString(),
                    event.proofId(),
                    event.voucherId(),
                    event.settlementId(),
                    event.authSessionId(),
                    event.requestId(),
                    false,
                    "NONE",
                    null
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
                runningReceived = event.isTopup()
                        ? runningReceived.add(event.amount())
                        : runningReceived.subtract(event.amount());
            }
        }

        for (LedgerEvent event : events) {
            BigDecimal cumulativeAmount = runningReceived.max(BigDecimal.ZERO);
            String formattedAmount = (event.isTopup() ? "+" : "-") + event.amount().toPlainString();
            boolean receivedSettlementRequired = event.direction() == LedgerDirection.RECEIVE
                    && event.unsettledAmount().compareTo(BigDecimal.ZERO) > 0;
            String receivedSettlementState = receivedSettlementRequired
                    ? "UNSETTLED"
                    : event.settledAmount().compareTo(BigDecimal.ZERO) > 0
                        ? "SETTLED"
                        : "NONE";
            items.add(new LedgerHistoryItem(
                    event.id(),
                    event.date(),
                    event.title(),
                    event.memo(),
                    formattedAmount,
                    cumulativeAmount.toPlainString(),
                    cumulativeAmount.toPlainString(),
                    event.statusLabel(),
                    event.statusCode().name(),
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
                    event.settledAmount().toPlainString(),
                    event.proofId(),
                    event.voucherId(),
                    event.settlementId(),
                    event.authSessionId(),
                    event.requestId(),
                    receivedSettlementRequired,
                    receivedSettlementState,
                    receivedSettlementRequired ? event.proofId() : null
            ));
            if (event.affectsServerBalance()) {
                runningReceived = event.isTopup()
                        ? runningReceived.subtract(event.amount()).max(BigDecimal.ZERO)
                        : runningReceived.add(event.amount());
            }
        }
        return items;
    }

    private LedgerEvent toCollateralEvent(CollateralOperation operation) {
        boolean completed = operation.status() == CollateralOperationStatus.COMPLETED;
        boolean failed = operation.status() == CollateralOperationStatus.FAILED;
        PublicLedgerStatus statusCode = failed
                ? PublicLedgerStatus.FAILED
                : completed ? PublicLedgerStatus.CONFIRMED : PublicLedgerStatus.PENDING;
        String title = operation.operationType().name().equals("RELEASE") ? "담보해제" : "담보충전";
        String memo = readMetadataText(operation.metadataJson(), "description");
        if (memo.isBlank()) {
            memo = operation.operationType().name().equals("RELEASE") ? "오프라인 담보 해제 요청" : "오프라인 담보 충전 요청";
        }
        String walletAddress = firstNonBlank(
                readMetadataText(operation.metadataJson(), "walletAddress"),
                operation.deviceId()
        );

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
                walletAddress,
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
                BigDecimal.ZERO,
                "",
                "",
                "",
                "",
                "",
                0L
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
        String requestId = firstNonBlank(
                payload.path("requestId").asText(""),
                payload.path("sessionId").asText(""),
                payload.path("requestIntentId").asText("")
        );
        String authSessionId = payload.path("authSessionId").asText("");
        String settlementId = payload.path("settlementId").asText("");
        long offlineTxSequence = Math.max(0L, payload.path("offlineTxSequence").asLong(0L));
        LedgerDirection direction = senderOwned ? LedgerDirection.SEND : LedgerDirection.RECEIVE;
        boolean receiverSettled = direction == LedgerDirection.RECEIVE
                && normalizeAmount(proof.receivedSettledAmount()).compareTo(BigDecimal.ZERO) > 0;
        PublicLedgerStatus statusCode = toPublicLedgerStatus(proof, receiverSettled);
        BigDecimal receivedUnsettledAmount = senderOwned
                ? BigDecimal.ZERO
                : resolveReceivedUnsettledAmount(proof, statusCode);
        BigDecimal displayAmount = senderOwned
                ? proof.amount().abs()
                : resolveReceivedDisplayAmount(proof, payload, receivedUnsettledAmount, statusCode);

        return new LedgerEvent(
                "proof:" + proof.id(),
                direction,
                counterparty,
                description,
                displayAmount,
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
                statusCode == PublicLedgerStatus.CONFIRMED || statusCode == PublicLedgerStatus.SETTLED,
                !senderOwned,
                receivedUnsettledAmount,
                senderOwned ? BigDecimal.ZERO : resolveReceivedSettledAmount(proof, payload),
                proof.id(),
                proof.voucherId(),
                settlementId,
                authSessionId,
                requestId,
                offlineTxSequence
        );
    }

    private LedgerEvent toReceivedSettlementEvent(OfflinePaymentProof proof, LedgerEvent receiveEvent) {
        BigDecimal settledAmount = resolveReceivedSettledAmount(proof, jsonService.readTree(proof.rawPayloadJson()));
        if (settledAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        OffsetDateTime time = proof.receivedCollateralSettledAt() != null
                ? proof.receivedCollateralSettledAt()
                : proof.updatedAt() != null
                    ? proof.updatedAt()
                    : receiveEvent.time();
        return new LedgerEvent(
                "received-settlement:" + proof.id(),
                LedgerDirection.RECEIVE,
                "수취 정산",
                "미정산 결제금 KORION wallet 반영",
                settledAmount.abs(),
                PublicLedgerStatus.SETTLED,
                statusLabel(PublicLedgerStatus.SETTLED),
                receiveEvent.network(),
                receiveEvent.assetCode(),
                receiveEvent.walletAddress(),
                time,
                "Offline Receive Settlement",
                "Offline",
                "정산",
                receiveEvent.counterpartyName(),
                "0.000000",
                "SETTLEMENT",
                receiveEvent.paymentMethod(),
                true,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                proof.id(),
                proof.voucherId(),
                receiveEvent.settlementId(),
                receiveEvent.authSessionId(),
                receiveEvent.requestId(),
                receiveEvent.offlineTxSequence()
        );
    }

    private PublicLedgerStatus toPublicLedgerStatus(OfflinePaymentProof proof, boolean receiverSettled) {
        return switch (proof.status()) {
            case FAILED -> PublicLedgerStatus.FAILED;
            case REJECTED -> toRejectedPublicLedgerStatus(proof.reasonCode());
            case EXPIRED -> PublicLedgerStatus.EXPIRED;
            case CONFLICTED -> PublicLedgerStatus.LOCKED;
            case SETTLED -> receiverSettled ? PublicLedgerStatus.SETTLED : PublicLedgerStatus.CONFIRMED;
            case ISSUED, UPLOADED, VALIDATING, VERIFIED_OFFLINE, CONSUMED_PENDING_SETTLEMENT -> PublicLedgerStatus.PENDING;
        };
    }

    private PublicLedgerStatus toRejectedPublicLedgerStatus(String reasonCode) {
        String normalized = reasonCode == null ? "" : reasonCode.trim().toUpperCase();
        return switch (normalized) {
            case "POLICY_REJECTED", "POLICY_VIOLATION", "USER_REJECTED", "RECEIVER_REJECTED" -> PublicLedgerStatus.REJECTED;
            default -> PublicLedgerStatus.FAILED;
        };
    }

    private BigDecimal resolveReceivedUnsettledAmount(OfflinePaymentProof proof, PublicLedgerStatus statusCode) {
        BigDecimal unsettledAmount = normalizeAmount(proof.receivedUnsettledAmount());
        if (statusCode != PublicLedgerStatus.PENDING || unsettledAmount.compareTo(BigDecimal.ZERO) > 0) {
            return normalizeReceivedAmount(proof, unsettledAmount);
        }
        if (proof.status() == OfflineProofStatus.REJECTED) {
            return unsettledAmount;
        }
        BigDecimal settledAmount = resolveReceivedSettledAmount(proof, jsonService.readTree(proof.rawPayloadJson()));
        if (settledAmount.compareTo(BigDecimal.ZERO) > 0) {
            return unsettledAmount;
        }
        return calculateReceiverAmount(proof, jsonService.readTree(proof.rawPayloadJson()));
    }

    private BigDecimal resolveReceivedSettledAmount(OfflinePaymentProof proof, JsonNode payload) {
        return normalizeReceivedAmount(proof, normalizeAmount(proof.receivedSettledAmount()));
    }

    private BigDecimal resolveReceivedDisplayAmount(
            OfflinePaymentProof proof,
            JsonNode payload,
            BigDecimal receivedUnsettledAmount,
            PublicLedgerStatus statusCode
    ) {
        if (statusCode == PublicLedgerStatus.SETTLED) {
            BigDecimal settledAmount = resolveReceivedSettledAmount(proof, payload);
            if (settledAmount.compareTo(BigDecimal.ZERO) > 0) {
                return settledAmount;
            }
        }
        if (receivedUnsettledAmount.compareTo(BigDecimal.ZERO) > 0) {
            return normalizeReceivedAmount(proof, receivedUnsettledAmount);
        }
        return calculateReceiverAmount(proof, payload);
    }

    private BigDecimal normalizeReceivedAmount(OfflinePaymentProof proof, BigDecimal amount) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        BigDecimal grossAmount = normalizeAmount(proof.amount());
        if (grossAmount.compareTo(BigDecimal.ZERO) > 0 && normalizedAmount.compareTo(grossAmount) >= 0) {
            return calculateReceiverAmount(proof, jsonService.readTree(proof.rawPayloadJson()));
        }
        return normalizedAmount;
    }

    private BigDecimal calculateReceiverAmount(OfflinePaymentProof proof, JsonNode payload) {
        String assetCode = payload.path("token").asText(payload.path("assetCode").asText(properties.assetCode()));
        return feeCalculator.calculateReceiverAmount(assetCode, proof.amount());
    }

    private OffsetDateTime resolveProofTime(OfflinePaymentProof proof) {
        JsonNode payload = jsonService.readTree(proof.rawPayloadJson());
        String estimatedServerTime = payload.path("estimatedServerTime").asText("");
        if (!estimatedServerTime.isBlank()) {
            try {
                return OffsetDateTime.parse(estimatedServerTime);
            } catch (RuntimeException ignored) {
                // Fall through to persisted server timestamps for legacy or malformed payloads.
            }
        }
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
            if ((node.isMissingNode() || node.isNull()) && root.has("metadata")) {
                node = root.path("metadata").path(fieldName);
            }
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

    private String statusLabel(PublicLedgerStatus statusCode) {
        return switch (statusCode) {
            case SETTLED -> "정산완료";
            case CONFIRMED -> "검증완료";
            case FAILED -> "실패";
            case EXPIRED -> "만료";
            case REJECTED -> "거절";
            case LOCKED -> "잠금";
            case PENDING -> "대기";
        };
    }

    public record LedgerHistoryResponse(
            String assetCode,
            List<LedgerHistoryItem> sentItems,
            List<LedgerHistoryItem> receivedItems,
            String totalReceivedAmount,
            String refreshedAt,
            int page,
            int size,
            boolean sentHasNext,
            boolean receivedHasNext
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
            String settledAmount,
            String proofId,
            String voucherId,
            String settlementId,
            String authSessionId,
            String requestId,
            boolean receivedSettlementRequired,
            String receivedSettlementState,
            String receivedSettlementProofId
    ) {}

    public record HubProjectionResponse(
            String deviceId,
            long userId,
            String assetCode,
            String tab,
            List<LedgerHistoryItem> items,
            boolean hasNext,
            String totalReceivedAmount,
            String refreshedAt,
            int page,
            int size
    ) {}

    public record HubSummaryResponse(
            String deviceId,
            long userId,
            String assetCode,
            String unsettledReceivedAmount,
            String offlineAvailableAmount,
            String totalCollateralAmount,
            int failedCount,
            int pendingCount,
            String refreshedAt
    ) {}

    private record LedgerEvent(
            String id,
            LedgerDirection direction,
            String title,
            String memo,
            BigDecimal amount,
            PublicLedgerStatus statusCode,
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
            BigDecimal settledAmount,
            String proofId,
            String voucherId,
            String settlementId,
            String authSessionId,
            String requestId,
            long offlineTxSequence
    ) {
        String date() {
            return String.format("%02d.%02d", time.getMonthValue(), time.getDayOfMonth());
        }
    }

    private enum LedgerDirection {
        SEND,
        RECEIVE
    }

    private enum PublicLedgerStatus {
        PENDING,
        CONFIRMED,
        SETTLED,
        FAILED,
        EXPIRED,
        REJECTED,
        LOCKED
    }
}
