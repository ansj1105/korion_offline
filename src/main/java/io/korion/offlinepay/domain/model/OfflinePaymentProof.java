package io.korion.offlinepay.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
        String rawPayloadJson,
        OffsetDateTime createdAt
) {}
