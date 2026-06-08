package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
            new JsonService(new ObjectMapper())
    );

    @Test
    void resolvesAppSuffixReceiverAsReceivedLedgerItem() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = settledProof("app-suffix:e7eaeaa7");
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 200)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 200)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 200);

        assertEquals(1, response.receivedItems().size());
        assertEquals("Offline Receive", response.receivedItems().get(0).transactionType());
        assertEquals("47ba2d8b-5b95-4510-8b23-007957e4fe46", response.receivedItems().get(0).walletAddress());
        assertEquals("1.00000000", response.receivedItems().get(0).unsettledAmount());
        assertEquals("0", response.receivedItems().get(0).settledAmount());
    }

    @Test
    void excludesRejectedReceivedProofFromUnsettledBalanceAndTotal() {
        String receiverDeviceId = "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7";
        Device receiverDevice = device(receiverDeviceId, 39L);
        OfflinePaymentProof proof = rejectedProof("app-suffix:e7eaeaa7");
        when(deviceRepository.findActiveByUserId(39L)).thenReturn(List.of(receiverDevice));
        when(deviceRepository.findByDeviceId("47ba2d8b-5b95-4510-8b23-007957e4fe46")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 200)).thenReturn(List.of(proof));
        when(collateralOperationRepository.findRecentByUserIdAndAssetCode(39L, "KORI", 200)).thenReturn(List.of());
        when(collateralRepository.findAggregateByUserIdAndAssetCode(39L, "KORI")).thenReturn(Optional.empty());

        OfflineLedgerService.LedgerHistoryResponse response = service.getLedgerHistory(39L, "KORI", 200);

        assertEquals(1, response.receivedItems().size());
        assertEquals("FAILED", response.receivedItems().get(0).statusCode());
        assertEquals("0", response.receivedItems().get(0).balance());
        assertEquals("0", response.receivedItems().get(0).unsettledAmount());
        assertEquals("0", response.receivedItems().get(0).settledAmount());
        assertEquals("0", response.totalReceivedAmount());
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

    private OfflinePaymentProof rejectedProof(String receiverDeviceId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T04:45:04Z");
        return new OfflinePaymentProof(
                "c97ec0ca-c5a0-474d-9798-60d68017ee04",
                "batch-id",
                "voucher_1779133486894",
                "collateral-id",
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                receiverDeviceId,
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
                "INVALID_DEVICE_SIGNATURE",
                BigDecimal.ZERO,
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
