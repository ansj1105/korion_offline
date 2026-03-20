package io.korion.offlinepay.application.factory;

import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.SettlementRequest;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SettlementSyncCommandFactory {

    public CoinManageSettlementPort.SettlementLedgerCommand createLedgerCommand(
            CollateralLock collateral,
            String proofId,
            BigDecimal amount,
            SettlementRequest request,
            String settlementStatus,
            String releaseAction,
            boolean conflictDetected
    ) {
        return new CoinManageSettlementPort.SettlementLedgerCommand(
                request.id(),
                request.batchId(),
                collateral.id(),
                proofId,
                collateral.userId(),
                collateral.deviceId(),
                collateral.assetCode(),
                amount,
                settlementStatus,
                releaseAction,
                conflictDetected
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
