package io.korion.offlinepay.application.port;

import java.math.BigDecimal;

public interface FoxCoinHistoryPort {

    void recordSettlementHistory(SettlementHistoryCommand command);

    record SettlementHistoryCommand(
            String settlementId,
            String transferRef,
            String batchId,
            String collateralId,
            String proofId,
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal amount,
            BigDecimal feeAmount,
            String settlementStatus,
            String historyType
    ) {}
}
