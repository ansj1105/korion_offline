package io.korion.offlinepay.contracts.internal;

public record CoinManageCompensateSettlementContract(
        String settlementId,
        String batchId,
        String collateralId,
        String proofId,
        String userId,
        String deviceId,
        String assetCode,
        String amount,
        String releaseAction,
        String proofFingerprint,
        String compensationReason
) {}
