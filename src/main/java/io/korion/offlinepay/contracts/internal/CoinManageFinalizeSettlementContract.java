package io.korion.offlinepay.contracts.internal;

public record CoinManageFinalizeSettlementContract(
        String settlementId,
        String batchId,
        String collateralId,
        String proofId,
        String userId,
        String deviceId,
        String assetCode,
        String amount,
        String settlementStatus,
        String releaseAction,
        boolean conflictDetected,
        String proofFingerprint,
        String newStateHash,
        String previousHash,
        long monotonicCounter,
        String nonce,
        String signature
) {}
