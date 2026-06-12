package io.korion.offlinepay.domain.model;

import java.math.BigDecimal;
import java.util.Map;

public record OfflinePayLocalEvidence(
        String proofId,
        String voucherId,
        String sessionId,
        String direction,
        String uploaderType,
        String uploaderDeviceId,
        String senderDeviceId,
        String receiverDeviceId,
        BigDecimal amount,
        Long counter,
        String previousHash,
        String hashChainHead,
        String nonce,
        String signature,
        String canonicalPayload,
        Map<String, Object> rawPayload,
        String verificationStatus,
        String verificationDetail,
        String matchedProofId
) {}
