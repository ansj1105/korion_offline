package io.korion.offlinepay.contracts.internal;

import java.math.BigDecimal;

public record FoxCoinRecordSettlementHistoryContract(
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
