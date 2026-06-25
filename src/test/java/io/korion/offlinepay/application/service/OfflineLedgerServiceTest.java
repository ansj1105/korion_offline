package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.application.service.settlement.OfflinePaySettlementFeeCalculator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OfflineLedgerServiceTest {

    private final DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
    private final CollateralRepository collateralRepository = Mockito.mock(CollateralRepository.class);
    private final CollateralOperationRepository collateralOperationRepository = Mockito.mock(CollateralOperationRepository.class);
    private final OfflinePaymentProofRepository proofRepository = Mockito.mock(OfflinePaymentProofRepository.class);
    private final OfflinePayDeviceIdentifierResolver deviceIdentifierResolver = new OfflinePayDeviceIdentifierResolver(deviceRepository);
    private final OfflineLedgerService service = new OfflineLedgerService(
            deviceRepository,
            collateralRepository,
            collateralOperationRepository,
            proofRepository,
            deviceIdentifierResolver,
            new AppProperties("KORI", 0, 0, 0, null, null, null, null, null, null),
            new JsonService(new ObjectMapper()),
            new OfflinePaySettlementFeeCalculator()
    );

    @Test
    void hubProjectionRejectsDeviceOwnedByDifferentUserWhenExpectedUserIsProvided() {
        when(deviceRepository.findByUserIdAndDeviceId(39L, "shared-device")).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getHubProjection("shared-device", 39L, "RECEIVED", "KORI", 30, 0)
        );
    }

    @Test
    void hubProjectionUsesExpectedUserScopedDeviceWhenProvided() {
        Device device = device("shared-device", 1474L);
        when(deviceRepository.findByUserIdAndDeviceId(1474L, "shared-device")).thenReturn(Optional.of(device));
        when(deviceRepository.findActiveByUserId(1474L)).thenReturn(List.of(device));
        when(proofRepository.findRecentByUserIdAndAssetCode(1474L, "KORI", 31)).thenReturn(List.of());
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(1474L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(1474L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.HubProjectionResponse response =
                service.getHubProjection("shared-device", 1474L, "RECEIVED", "KORI", 30, 0);

        assertEquals(1474L, response.userId());
        assertEquals("shared-device", response.deviceId());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void resolvesAppSuffixReceiverAsReceivedLedgerItem() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = settledProof("app-suffix:e7eaeaa7");
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 200);

        assertEquals(1, response.receivedItems().size());
        assertEquals("Offline Receive", response.receivedItems().get(0).transactionType());
        assertEquals("CONFIRMED", response.receivedItems().get(0).statusCode());
        assertTrue(response.receivedItems().stream().noneMatch(item -> "COMPLETED".equals(item.statusCode())));
        assertEquals("47ba2d8b-5b95-4510-8b23-007957e4fe46", response.receivedItems().get(0).walletAddress());
        assertEquals("0.999000", response.receivedItems().get(0).unsettledAmount());
        assertEquals("0", response.receivedItems().get(0).settledAmount());
        assertTrue(response.receivedItems().get(0).receivedSettlementRequired());
        assertEquals("UNSETTLED", response.receivedItems().get(0).receivedSettlementState());
        assertEquals("c97ec0ca-c5a0-474d-9798-60d68017ee04", response.receivedItems().get(0).receivedSettlementProofId());
    }

    @Test
    void hubSummaryDoesNotCountConfirmedReceivedProofsAsPending() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = settledProof("app-suffix:e7eaeaa7");
        when(deviceRepository.findByDeviceId(receiverDeviceId)).thenReturn(Optional.of(receiverDevice));
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 300)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 300)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.HubSummaryResponse response = service.getHubSummary(receiverDeviceId, "KORI");

        assertEquals("0.999000", response.unsettledReceivedAmount());
        assertEquals(0, response.pendingCount());
        assertEquals(0, response.failedCount());
    }

    @Test
    void hubSummaryDoesNotCountSettledReceivedProofWithStaleUnsettledAmount() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = staleSettledReceivedProof("app-suffix:e7eaeaa7");
        when(deviceRepository.findByDeviceId(receiverDeviceId)).thenReturn(Optional.of(receiverDevice));
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 300)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 300)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.HubSummaryResponse response = service.getHubSummary(receiverDeviceId, "KORI");

        assertEquals("0", response.unsettledReceivedAmount());
        assertEquals(0, response.pendingCount());
        assertEquals(0, response.failedCount());
    }

    @Test
    void usesNestedCollateralOperationWalletAddressForLedgerItem() {
        CollateralOperation operation = new CollateralOperation(
                "operation-1",
                null,
                1761L,
                "device-1",
                "KORI",
                CollateralOperationType.TOPUP,
                new BigDecimal("2.000000"),
                CollateralOperationStatus.FAILED,
                "topup:device-1:test",
                "INSUFFICIENT_BALANCE",
                "{\"metadata\":{\"walletAddress\":\"T2J39DJ111111111111111111111111\"}}",
                OffsetDateTime.parse("2026-06-14T17:14:05Z"),
                OffsetDateTime.parse("2026-06-14T17:14:05Z")
        );
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(1761L, "KORI", 31)).thenReturn(List.of(operation));
        when(proofRepository.findRecentByUserIdAndAssetCode(1761L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(1761L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(1761L, "KORI", 30);

        assertEquals(1, response.sentItems().size());
        assertEquals("T2J39DJ111111111111111111111111", response.sentItems().get(0).walletAddress());
        assertEquals("FAILED", response.sentItems().get(0).statusCode());
    }

    @Test
    void ordersOfflineSentLedgerItemsByEstimatedServerTimeThenSequence() {
        Device senderDevice = device("device-1", 1L);
        OfflinePaymentProof lowerSequence = settledSentProofWithHybridTime(
                "proof-sequence-11",
                "voucher-sequence-11",
                "nonce-sequence-11",
                "2026-06-11T03:02:03Z",
                11
        );
        OfflinePaymentProof higherSequence = settledSentProofWithHybridTime(
                "proof-sequence-12",
                "voucher-sequence-12",
                "nonce-sequence-12",
                "2026-06-11T03:02:03Z",
                12
        );
        when(deviceRepository.findActiveByUserId(1L)).thenReturn(List.of(senderDevice));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(senderDevice));
        when(deviceRepository.findByDeviceId("device-2")).thenReturn(Optional.empty());
        when(proofRepository.findRecentByUserIdAndAssetCode(1L, "KORI", 31))
                .thenReturn(List.of(lowerSequence, higherSequence));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(1L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(1L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(1L, "KORI", 30);

        assertEquals(2, response.sentItems().size());
        assertEquals("proof-sequence-12", response.sentItems().get(0).proofId());
        assertEquals("proof-sequence-11", response.sentItems().get(1).proofId());
    }

    @Test
    void emitsPositiveReceivedSettlementLedgerItemWhenReceivedAmountIsSettled() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = settledReceivedProof("app-suffix:e7eaeaa7");
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 200);

        assertEquals(2, response.receivedItems().size());
        assertEquals("Offline Receive Settlement", response.receivedItems().get(0).transactionType());
        assertEquals("SETTLED", response.receivedItems().get(0).statusCode());
        assertEquals("+0.999000", response.receivedItems().get(0).amount());
        assertEquals("0", response.receivedItems().get(0).unsettledAmount());
        assertEquals("0", response.receivedItems().get(0).settledAmount());
        assertEquals("Offline Receive", response.receivedItems().get(1).transactionType());
        assertEquals("SETTLED", response.receivedItems().get(1).statusCode());
        assertEquals("+0.999000", response.receivedItems().get(1).amount());
        assertEquals("0", response.receivedItems().get(1).unsettledAmount());
        assertEquals("SETTLED", response.receivedItems().get(1).receivedSettlementState());
        assertEquals("0.999000", response.totalReceivedAmount());
    }

    @Test
    void marksZeroUnsettledReceivedProofAsSettled() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = clearedReceivedProof("app-suffix:e7eaeaa7");
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 200);

        assertEquals(1, response.receivedItems().size());
        assertEquals("Offline Receive", response.receivedItems().get(0).transactionType());
        assertEquals("SETTLED", response.receivedItems().get(0).statusCode());
        assertEquals("0", response.receivedItems().get(0).unsettledAmount());
        assertEquals("0", response.receivedItems().get(0).settledAmount());
        assertFalse(response.receivedItems().get(0).receivedSettlementRequired());
        assertEquals("SETTLED", response.receivedItems().get(0).receivedSettlementState());
        assertEquals("0", response.totalReceivedAmount());
    }

    @Test
    void excludesRejectedReceivedProofFromUnsettledBalanceAndTotal() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = rejectedProof("app-suffix:e7eaeaa7", "INVALID_DEVICE_SIGNATURE");
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 200);

        assertEquals(1, response.receivedItems().size());
        assertEquals("FAILED", response.receivedItems().get(0).statusCode());
        assertEquals("0", response.receivedItems().get(0).balance());
        assertEquals("0", response.receivedItems().get(0).unsettledAmount());
        assertEquals("0", response.receivedItems().get(0).settledAmount());
        assertEquals("NONE", response.receivedItems().get(0).receivedSettlementState());
        assertEquals("0", response.totalReceivedAmount());
    }

    @Test
    void mapsRejectedCounterGapReceivedProofToFailedPublicStatus() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = rejectedProof("app-suffix:e7eaeaa7", "COUNTER_GAP");
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 200);

        assertEquals(1, response.receivedItems().size());
        assertEquals("FAILED", response.receivedItems().get(0).statusCode());
        assertEquals("0", response.receivedItems().get(0).unsettledAmount());
        assertEquals("0", response.receivedItems().get(0).settledAmount());
        assertEquals("0", response.totalReceivedAmount());
    }

    @Test
    void keepsFinanciallyHonoredRejectedReceivedProofInUnsettledSummaryAndRows() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = rejectedProof(
                "app-suffix:e7eaeaa7",
                "SERVER_AVAILABLE_AMOUNT_EXCEEDED",
                new BigDecimal("0.99900000")
        );
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId(receiverDeviceId)).thenReturn(Optional.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of(proof));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 300)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of());
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 300)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse history = service.getLedgerHistory(39L, "KORI", 200);
        OfflineLedgerService.HubSummaryResponse summary = service.getHubSummary(receiverDeviceId, "KORI");

        assertEquals(1, history.receivedItems().size());
        assertEquals("FAILED", history.receivedItems().get(0).statusCode());
        assertEquals("0.99900000", history.receivedItems().get(0).unsettledAmount());
        assertEquals("0.99900000", history.totalReceivedAmount());
        assertEquals("0.99900000", summary.unsettledReceivedAmount());
        assertEquals(1, summary.failedCount());
    }

    @Test
    void mapsRejectedTransportInterruptedReceivedProofToFailedPublicStatus() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = rejectedProof("app-suffix:e7eaeaa7", "SEND_INTERRUPTED");
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 200);

        assertEquals(1, response.receivedItems().size());
        assertEquals("FAILED", response.receivedItems().get(0).statusCode());
        assertEquals("0", response.receivedItems().get(0).unsettledAmount());
        assertEquals("0", response.receivedItems().get(0).settledAmount());
        assertEquals("0", response.totalReceivedAmount());
    }

    @Test
    void hubProjectionResolvesUserFromDeviceIdAndReturnsRequestedTabOnly() {
        Device senderDevice = device("device-1", 1L);
        OfflinePaymentProof proof = settledSentProofWithHybridTime(
                "proof-1",
                "voucher-1",
                "nonce-1",
                "2026-06-11T03:02:03Z",
                1
        );
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(senderDevice));
        when(deviceRepository.findActiveByUserId(1L)).thenReturn(List.of(senderDevice));
        when(deviceRepository.findByDeviceId("device-2")).thenReturn(Optional.empty());
        when(proofRepository.findRecentByUserIdAndAssetCode(1L, "KORI", 31)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(1L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(1L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.HubProjectionResponse response = service.getHubProjection(
                "device-1",
                "SENT",
                "KORI",
                30,
                0
        );

        assertEquals(1L, response.userId());
        assertEquals("SENT", response.tab());
        assertEquals(1, response.items().size());
        assertEquals("proof-1", response.items().get(0).proofId());
        assertEquals("voucher-1", response.items().get(0).requestId());
    }

    @Test
    void doesNotReassignReceivedProofWhenDeviceOwnerChanges() {
        Device currentDeviceOwner = device("shared-receiver-device", 1474L);
        OfflinePaymentProof proof = settledProof("shared-receiver-device");
        when(deviceRepository.findActiveByUserId(1474L)).thenReturn(List.of(currentDeviceOwner));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("shared-receiver-device")).thenReturn(Optional.of(currentDeviceOwner));
        when(proofRepository.findRecentByUserIdAndAssetCode(1474L, "KORI", 31)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(1474L, "KORI", 31)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(1474L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(1474L, "KORI", 30);

        assertTrue(response.sentItems().isEmpty());
        assertTrue(response.receivedItems().isEmpty());
    }

    @Test
    void doesNotRepeatLedgerPageAfterFetchWindowCap() {
        Device receiverDevice = device("receiver-device", 39L);
        Device senderDevice = device("sender-device", 1L);
        List<OfflinePaymentProof> proofs = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            proofs.add(settledReceivedProof(
                    "proof-page-" + index,
                    "receiver-device",
                    OffsetDateTime.parse("2026-06-23T00:00:00Z").plusSeconds(index)
            ));
        }

        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));
        when(deviceRepository.findByDeviceId("sender-device")).thenReturn(Optional.of(senderDevice));
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 300)).thenReturn(proofs);
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 300)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 30, 14);

        assertEquals(14, response.page());
        assertEquals(0, response.receivedItems().size());
        assertFalse(response.receivedHasNext());
    }

    private Device device(String deviceId, long userId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T04:45:04Z");
        return new Device(
                "device-row",
                deviceId,
                userId,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                now,
                now
        );
    }

    private OfflinePaymentProof settledProof(String receiverDeviceId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T04:45:04Z");
        return new OfflinePaymentProof(
                "c97ec0ca-c5a0-474d-9798-60d68017ee04",
                "batch-id",
                "voucher_1779133486894",
                "collateral-id",
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                receiverDeviceId,
                1L,
                39L,
                1,
                1,
                1,
                "nonce",
                "hash",
                "previous-hash",
                "signature",
                new BigDecimal("1.00000000"),
                1779133504000L,
                1779137104000L,
                "{}",
                "SENDER",
                "MANUAL_SELECTION",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                "{\"paymentMethod\":\"BLE\",\"token\":\"KORI\",\"fee\":\"0.001000\"}",
                now,
                now,
                null,
                now,
                now,
                now,
                now
        );
    }

    private OfflinePaymentProof settledSentProofWithHybridTime(
            String proofId,
            String voucherId,
            String nonce,
            String estimatedServerTime,
            long offlineTxSequence
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T04:45:04Z");
        String rawPayload = """
                {
                  "requestId": "%s",
                  "txId": "%s",
                  "offlineTxSequence": %d,
                  "deviceTime": "2026-06-11T12:02:03Z",
                  "lastServerSyncTime": "2026-06-11T03:00:00Z",
                  "estimatedServerTime": "%s",
                  "elapsedTimeMs": 123000,
                  "paymentMethod": "BLE",
                  "token": "KORI",
                  "fee": "0.001000"
                }
                """.formatted(voucherId, voucherId, offlineTxSequence, estimatedServerTime);
        return new OfflinePaymentProof(
                proofId,
                "batch-" + proofId,
                voucherId,
                "collateral-id",
                "device-1",
                "device-2",
                1L,
                2L,
                1,
                1,
                offlineTxSequence,
                nonce,
                "hash-" + proofId,
                "previous-hash",
                "signature",
                new BigDecimal("1.00000000"),
                1779133504000L,
                1779137104000L,
                "{}",
                "SENDER",
                "MANUAL_SELECTION",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                rawPayload,
                now,
                now,
                null,
                now,
                now,
                now,
                now
        );
    }

    private OfflinePaymentProof settledReceivedProof(String receiverDeviceId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T04:45:04Z");
        OffsetDateTime settledAt = OffsetDateTime.parse("2026-05-19T04:46:04Z");
        return new OfflinePaymentProof(
                "c97ec0ca-c5a0-474d-9798-60d68017ee04",
                "batch-id",
                "voucher_1779133486894",
                "collateral-id",
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                receiverDeviceId,
                1L,
                39L,
                1,
                1,
                1,
                "nonce",
                "hash",
                "previous-hash",
                "signature",
                new BigDecimal("1.00000000"),
                1779133504000L,
                1779137104000L,
                "{}",
                "SENDER",
                "MANUAL_SELECTION",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                BigDecimal.ZERO,
                new BigDecimal("1.00000000"),
                "operation-1",
                "received-wallet-settlement:c97ec0ca-c5a0-474d-9798-60d68017ee04",
                settledAt,
                "{\"paymentMethod\":\"BLE\",\"token\":\"KORI\",\"fee\":\"0.001000\"}",
                now,
                now,
                null,
                now,
                now,
                settledAt,
                now
        );
    }

    private OfflinePaymentProof staleSettledReceivedProof(String receiverDeviceId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T04:45:04Z");
        OffsetDateTime settledAt = OffsetDateTime.parse("2026-05-19T04:46:04Z");
        return new OfflinePaymentProof(
                "77cc4410-83c3-4c14-99a3-4ba7b4581dd6",
                "batch-id",
                "voucher_1779133486894",
                "collateral-id",
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                receiverDeviceId,
                1L,
                39L,
                1,
                1,
                1,
                "nonce",
                "hash",
                "previous-hash",
                "signature",
                new BigDecimal("2.00000000"),
                1779133504000L,
                1779137104000L,
                "{}",
                "SENDER",
                "MANUAL_SELECTION",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                new BigDecimal("1.998000"),
                new BigDecimal("1.998000"),
                "operation-1",
                "received-wallet-settlement:77cc4410-83c3-4c14-99a3-4ba7b4581dd6",
                settledAt,
                "{\"paymentMethod\":\"BLE\",\"token\":\"KORI\",\"fee\":\"0.002000\"}",
                now,
                now,
                null,
                now,
                now,
                settledAt,
                now
        );
    }

    private OfflinePaymentProof clearedReceivedProof(String receiverDeviceId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T04:45:04Z");
        return new OfflinePaymentProof(
                "c97ec0ca-c5a0-474d-9798-60d68017ee04",
                "batch-id",
                "voucher_1779133486894",
                "collateral-id",
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                receiverDeviceId,
                1L,
                39L,
                1,
                1,
                1,
                "nonce",
                "hash",
                "previous-hash",
                "signature",
                new BigDecimal("1.00000000"),
                1779133504000L,
                1779137104000L,
                "{}",
                "SENDER",
                "MANUAL_SELECTION",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                "admin-clear:test",
                null,
                "{\"paymentMethod\":\"BLE\",\"token\":\"KORI\",\"fee\":\"0.001000\"}",
                now,
                now,
                null,
                now,
                now,
                now,
                now
        );
    }

    private OfflinePaymentProof settledReceivedProof(String proofId, String receiverDeviceId, OffsetDateTime proofTime) {
        OffsetDateTime settledAt = proofTime.plusMinutes(1);
        return new OfflinePaymentProof(
                proofId,
                "batch-" + proofId,
                "voucher-" + proofId,
                "collateral-id",
                "sender-device",
                receiverDeviceId,
                1L,
                39L,
                1,
                1,
                1,
                "nonce-" + proofId,
                "hash-" + proofId,
                "previous-hash",
                "signature",
                new BigDecimal("1.00000000"),
                1779133504000L,
                1779137104000L,
                "{}",
                "SENDER",
                "MANUAL_SELECTION",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                BigDecimal.ZERO,
                new BigDecimal("1.00000000"),
                "operation-" + proofId,
                "received-wallet-settlement:" + proofId,
                settledAt,
                """
                {
                  "paymentMethod": "BLE",
                  "token": "KORI",
                  "fee": "0.001000",
                  "estimatedServerTime": "%s",
                  "offlineTxSequence": 1
                }
                """.formatted(proofTime),
                proofTime,
                proofTime,
                null,
                proofTime,
                proofTime,
                settledAt,
                proofTime
        );
    }

    private OfflinePaymentProof rejectedProof(String receiverDeviceId, String reasonCode) {
        return rejectedProof(receiverDeviceId, reasonCode, BigDecimal.ZERO);
    }

    private OfflinePaymentProof rejectedProof(String receiverDeviceId, String reasonCode, BigDecimal receivedUnsettledAmount) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T04:45:04Z");
        return new OfflinePaymentProof(
                "c97ec0ca-c5a0-474d-9798-60d68017ee04",
                "batch-id",
                "voucher_1779133486894",
                "collateral-id",
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                receiverDeviceId,
                1L,
                39L,
                1,
                1,
                1,
                "nonce",
                "hash",
                "previous-hash",
                "signature",
                new BigDecimal("1.00000000"),
                1779133504000L,
                1779137104000L,
                "{}",
                "SENDER",
                "MANUAL_SELECTION",
                OfflineProofStatus.REJECTED,
                reasonCode,
                receivedUnsettledAmount,
                BigDecimal.ZERO,
                null,
                null,
                null,
                "{\"paymentMethod\":\"BLE\",\"token\":\"KORI\",\"fee\":\"0.001000\"}",
                now,
                now,
                null,
                null,
                null,
                now,
                now
        );
    }
}
