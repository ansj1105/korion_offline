package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IssuedOfflineProof(
        String id,
        long userId,
        String deviceId,
        String collateralId,
        String assetCode,
        BigDecimal usableAmount,
        String proofNonce,
        String issuerKeyId,
        String issuerPublicKey,
        String issuerSignature,
        String issuedPayloadJson,
        IssuedProofStatus status,
        String consumedByProofId,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
