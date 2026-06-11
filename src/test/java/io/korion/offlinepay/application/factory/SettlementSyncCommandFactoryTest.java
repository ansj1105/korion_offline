package io.korion.offlinepay.application.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.korion.offlinepay.application.service.settlement.OfflinePaySettlementFeeCalculator;
import io.korion.offlinepay.application.service.settlement.ProofFingerprintService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SettlementSyncCommandFactoryTest {

    private final SettlementSyncCommandFactory factory = new SettlementSyncCommandFactory(
            new ProofFingerprintService(),
            new OfflinePaySettlementFeeCalculator()
    );

    @Test
    void settlementSyncUsesSenderDeviceInsteadOfCollateralOriginDevice() {
        CollateralLock collateral = collateral("collateral-old", "old-device");
        OfflinePaymentProof proof = proof("proof-1", collateral.id(), "current-sender-device");
        SettlementRequest request = request("settlement-1", collateral.id(), proof.id());
        Device receiverDevice = device("receiver-device", 77L);

        var ledgerCommand = factory.createLedgerCommand(
                collateral,
                proof,
                proof.amount(),
                request,
                SettlementStatus.SETTLED.name(),
                "RELEASE",
                false,
                receiverDevice
        );
        var historyCommand = factory.createHistoryCommand(
                collateral,
                proof,
                proof.amount(),
                request,
                SettlementStatus.SETTLED.name(),
                "RELEASE",
                false
        );

        assertEquals("current-sender-device", ledgerCommand.deviceId());
        assertEquals("current-sender-device", historyCommand.deviceId());
        assertEquals("receiver-device", ledgerCommand.receiverDeviceId());
    }

    private CollateralLock collateral(String id, String deviceId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new CollateralLock(
                id,
                35L,
                deviceId,
                "KORI",
                new BigDecimal("100.000000"),
                new BigDecimal("100.000000"),
                "root",
                1,
                CollateralStatus.LOCKED,
                "external-lock",
                now.plusDays(1),
                "{}",
                now,
                now
        );
    }

    private OfflinePaymentProof proof(String id, String collateralId, String senderDeviceId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new OfflinePaymentProof(
                id,
                "batch-1",
                "voucher-1",
                collateralId,
                senderDeviceId,
                "receiver-device",
                1,
                1,
                1L,
                "nonce-1",
                "new-state",
                "prev-state",
                "signature",
                new BigDecimal("10.000000"),
                123456789L,
                223456789L,
                "{}",
                "SENDER",
                "BLE",
                OfflineProofStatus.UPLOADED,
                null,
                "{}",
                now,
                now,
                null,
                null,
                null,
                now,
                now
        );
    }

    private SettlementRequest request(String id, String collateralId, String proofId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new SettlementRequest(
                id,
                "batch-1",
                collateralId,
                proofId,
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                now,
                now
        );
    }

    private Device device(String deviceId, long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Device(
                "device-row-" + deviceId,
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
}
