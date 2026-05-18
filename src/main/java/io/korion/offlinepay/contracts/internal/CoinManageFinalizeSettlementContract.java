package io.korion.offlinepay.contracts.internal;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoinManageFinalizeSettlementContract(
        String settlementId,
        String batchId,
        String collateralId,
        String proofId,
        String userId,
        String deviceId,
        String receiverUserId,
        String receiverDeviceId,
        String assetCode,
        String amount,
        String feeAmount,
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
