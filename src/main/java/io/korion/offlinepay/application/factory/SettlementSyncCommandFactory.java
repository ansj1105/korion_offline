package io.korion.offlinepay.application.factory;

import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementRequest;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SettlementSyncCommandFactory {

    private final io.korion.offlinepay.application.service.settlement.ProofFingerprintService proofFingerprintService;

    public SettlementSyncCommandFactory(io.korion.offlinepay.application.service.settlement.ProofFingerprintService proofFingerprintService) {
        this.proofFingerprintService = proofFingerprintService;
    }

    public CoinManageSettlementPort.SettlementLedgerCommand createLedgerCommand(
            CollateralLock collateral,
            OfflinePaymentProof proof,
            BigDecimal amount,
            SettlementRequest request,
            String settlementStatus,
            String releaseAction,
            boolean conflictDetected
    ) {
        String proofFingerprint = proofFingerprintService.computeFingerprint(
                request.id(),
                request.batchId(),
                collateral.id(),
                proof.id(),
                collateral.deviceId(),
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
                collateral.deviceId(),
                collateral.assetCode(),
                amount,
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

    public FoxCoinHistoryPort.SettlementHistoryCommand createHistoryCommand(
            CollateralLock collateral,
            String proofId,
            BigDecimal amount,
            SettlementRequest request,
            String settlementStatus,
            boolean conflictDetected
    ) {
        return new FoxCoinHistoryPort.SettlementHistoryCommand(
                request.id(),
                request.batchId(),
                collateral.id(),
                proofId,
                collateral.userId(),
                collateral.deviceId(),
                collateral.assetCode(),
                amount,
                settlementStatus,
                conflictDetected ? "OFFLINE_PAY_CONFLICT" : "OFFLINE_PAY_SETTLEMENT"
        );
    }
}
