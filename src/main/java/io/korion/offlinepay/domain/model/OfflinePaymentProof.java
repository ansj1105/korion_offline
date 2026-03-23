package io.korion.offlinepay.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import io.korion.offlinepay.domain.status.OfflineProofStatus;

public record OfflinePaymentProof(
        String id,
        String batchId,
        String voucherId,
        String collateralId,
        String senderDeviceId,
        String receiverDeviceId,
        int keyVersion,
        int policyVersion,
        long counter,
        String nonce,
        String hashChainHead,
        String previousHash,
        String signature,
        BigDecimal amount,
        long timestampMs,
        long expiresAtMs,
        String canonicalPayload,
        String uploaderType,
        String channelType,
        OfflineProofStatus status,
        String reasonCode,
        String rawPayloadJson,
        OffsetDateTime issuedAt,
        OffsetDateTime uploadedAt,
        OffsetDateTime consumedAt,
        OffsetDateTime verifiedAt,
        OffsetDateTime settledAt,
        OffsetDateTime updatedAt,
        OffsetDateTime createdAt
) {

    public OfflinePaymentProof(
            String id,
            String batchId,
            String voucherId,
            String collateralId,
            String senderDeviceId,
            String receiverDeviceId,
            int keyVersion,
            int policyVersion,
            long counter,
            String nonce,
            String hashChainHead,
            String previousHash,
            String signature,
            BigDecimal amount,
            long timestampMs,
            long expiresAtMs,
            String canonicalPayload,
            String uploaderType,
            String rawPayloadJson,
            OffsetDateTime createdAt
    ) {
        this(
                id,
                batchId,
                voucherId,
                collateralId,
                senderDeviceId,
                receiverDeviceId,
                keyVersion,
                policyVersion,
                counter,
                nonce,
                hashChainHead,
                previousHash,
                signature,
                amount,
                timestampMs,
                expiresAtMs,
                canonicalPayload,
                uploaderType,
                "UNKNOWN",
                OfflineProofStatus.ISSUED,
                null,
                rawPayloadJson,
                createdAt,
                createdAt,
                null,
                null,
                null,
                createdAt,
                createdAt
        );
    }

    public long monotonicCounter() {
        return counter;
    }

    public String newStateHash() {
        return hashChainHead;
    }

    public String prevStateHash() {
        return previousHash;
    }
}
