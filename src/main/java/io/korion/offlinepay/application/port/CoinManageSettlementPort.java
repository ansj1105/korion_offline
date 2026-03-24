package io.korion.offlinepay.application.port;

import java.math.BigDecimal;

public interface CoinManageSettlementPort {

    void finalizeSettlement(SettlementLedgerCommand command);

    void compensateSettlement(SettlementCompensationCommand command);

    record SettlementLedgerCommand(
            String settlementId,
            String batchId,
            String collateralId,
            String proofId,
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal amount,
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

    record SettlementCompensationCommand(
            String settlementId,
            String batchId,
            String collateralId,
            String proofId,
            long userId,
            String deviceId,
            String assetCode,
            BigDecimal amount,
            String releaseAction,
            String proofFingerprint,
            String compensationReason
    ) {}
}
