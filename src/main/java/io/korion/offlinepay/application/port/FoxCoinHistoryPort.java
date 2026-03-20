package io.korion.offlinepay.application.port;

import java.math.BigDecimal;

public interface FoxCoinHistoryPort {

    void recordSettlementHistory(SettlementHistoryCommand command);

    record SettlementHistoryCommand(
            String settlementId,
            String batchId,
            String collateralId,
            String proofId,
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal amount,
            String settlementStatus,
            String historyType
    ) {}
}
