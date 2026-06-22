package io.korion.offlinepay.application.factory;

import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.service.settlement.OfflinePaySettlementFeeCalculator;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementRequest;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SettlementSyncCommandFactory {

    private final io.korion.offlinepay.application.service.settlement.ProofFingerprintService proofFingerprintService;
    private final OfflinePaySettlementFeeCalculator feeCalculator;

    public SettlementSyncCommandFactory(
            io.korion.offlinepay.application.service.settlement.ProofFingerprintService proofFingerprintService,
            OfflinePaySettlementFeeCalculator feeCalculator
    ) {
        this.proofFingerprintService = proofFingerprintService;
        this.feeCalculator = feeCalculator;
    }

    public CoinManageSettlementPort.SettlementLedgerCommand createLedgerCommand(
            CollateralLock collateral,
            OfflinePaymentProof proof,
            BigDecimal amount,
            SettlementRequest request,
            String settlementStatus,
            String releaseAction,
            boolean conflictDetected,
            Device receiverDevice,
            boolean receiverWalletSettlementRequested
    ) {
        String senderDeviceId = proof.senderDeviceId();
        String proofFingerprint = proofFingerprintService.computeFingerprint(
                request.id(),
                request.batchId(),
                collateral.id(),
                proof.id(),
                senderDeviceId,
                proof.newStateHash(),
                proof.prevStateHash(),
                proof.monotonicCounter(),
                proof.nonce(),
                proof.signature()
        );
        return new CoinManageSettlementPort.SettlementLedgerCommand(
                request.id(),
                request.batchId(),
                collateral.id(),
                proof.id(),
                collateral.userId(),
                senderDeviceId,
                receiverDevice == null ? null : receiverDevice.userId(),
                receiverDevice == null ? null : receiverDevice.deviceId(),
                receiverWalletSettlementRequested,
                collateral.assetCode(),
                amount,
                settlementFeeAmount(collateral.assetCode(), amount, settlementStatus, releaseAction),
                settlementStatus,
                releaseAction,
                conflictDetected,
                proofFingerprint,
                proof.newStateHash(),
                proof.prevStateHash(),
                proof.monotonicCounter(),
                proof.nonce(),
                proof.signature()
        );
    }

    private BigDecimal settlementFeeAmount(String assetCode, BigDecimal amount, String settlementStatus, String releaseAction) {
        if (!"SETTLED".equalsIgnoreCase(settlementStatus) || !"RELEASE".equalsIgnoreCase(releaseAction)) {
            return BigDecimal.ZERO.setScale(6, java.math.RoundingMode.HALF_UP);
        }
        return feeCalculator.calculateFee(assetCode, amount);
    }

    public FoxCoinHistoryPort.SettlementHistoryCommand createHistoryCommand(
            CollateralLock collateral,
            OfflinePaymentProof proof,
            BigDecimal amount,
            SettlementRequest request,
            String settlementStatus,
            String releaseAction,
            boolean conflictDetected
    ) {
        return new FoxCoinHistoryPort.SettlementHistoryCommand(
                request.id(),
                request.id(), // transferRef = settlementId for sender
                request.batchId(),
                collateral.id(),
                proof.id(),
                collateral.userId(),
                proof.senderDeviceId(),
                collateral.assetCode(),
                amount,
                settlementFeeAmount(collateral.assetCode(), amount, settlementStatus, releaseAction),
                settlementStatus,
                conflictDetected ? "OFFLINE_PAY_CONFLICT" : "OFFLINE_PAY_SETTLEMENT"
        );
    }

    public FoxCoinHistoryPort.SettlementHistoryCommand createReceiverHistoryCommand(
            CollateralLock collateral,
            String proofId,
            BigDecimal amount,
            SettlementRequest request,
            String settlementStatus,
            String releaseAction,
            Device receiverDevice
    ) {
        return new FoxCoinHistoryPort.SettlementHistoryCommand(
                request.id(),
                request.id() + ":R", // transferRef distinct from sender to avoid unique key conflict
                request.batchId(),
                collateral.id(),
                proofId,
                receiverDevice.userId(),
                receiverDevice.deviceId(),
                collateral.assetCode(),
                receiverHistoryAmount(collateral.assetCode(), amount, settlementStatus),
                settlementFeeAmount(collateral.assetCode(), amount, settlementStatus, releaseAction),
                settlementStatus,
                "OFFLINE_PAY_RECEIVE"
        );
    }

    private BigDecimal receiverHistoryAmount(String assetCode, BigDecimal amount, String settlementStatus) {
        if (!"SETTLED".equalsIgnoreCase(settlementStatus)) {
            return amount;
        }
        return feeCalculator.calculateReceiverAmount(assetCode, amount);
    }
}
