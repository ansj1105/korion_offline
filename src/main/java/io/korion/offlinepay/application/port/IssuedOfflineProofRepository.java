package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface IssuedOfflineProofRepository {

    IssuedOfflineProof save(
            String proofId,
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
            OffsetDateTime expiresAt
    );

    Optional<IssuedOfflineProof> findById(String proofId);

    Optional<IssuedOfflineProof> findLatestActiveByUserIdAndDeviceIdAndAssetCode(long userId, String deviceId, String assetCode);

    boolean existsActiveByCollateralId(String collateralId);

    void updateStatus(String proofId, IssuedProofStatus status, String consumedByProofId);
}
