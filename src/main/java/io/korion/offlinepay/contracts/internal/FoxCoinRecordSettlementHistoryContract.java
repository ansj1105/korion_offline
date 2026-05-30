package io.korion.offlinepay.contracts.internal;

import java.math.BigDecimal;

public record FoxCoinRecordSettlementHistoryContract(
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
