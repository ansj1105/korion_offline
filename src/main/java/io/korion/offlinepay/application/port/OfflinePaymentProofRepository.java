package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import java.math.BigDecimal;

public interface OfflinePaymentProofRepository {

    OfflinePaymentProof save(
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
            String rawPayloadJson
    );

    void updateLifecycle(String proofId, OfflineProofStatus status, String reasonCode, boolean consumed, boolean verified, boolean settled);

    java.util.Optional<OfflinePaymentProof> findById(String proofId);

    java.util.Optional<OfflinePaymentProof> findByVoucherId(String voucherId);

    java.util.Optional<OfflinePaymentProof> findBySenderNonce(String senderDeviceId, String nonce);

    java.util.Optional<OfflinePaymentProof> findBySenderRequestId(String senderDeviceId, String requestId);

    java.util.List<OfflinePaymentProof> findByCollateralId(String collateralId);

    java.util.List<OfflinePaymentProof> findBySenderDeviceUserAndAsset(String senderDeviceId, long userId, String assetCode);

    java.util.List<OfflinePaymentProof> findRecent(int size, OfflineProofStatus status, String channelType);

    java.util.List<OfflinePaymentProof> findRecentByUserIdAndAssetCode(long userId, String assetCode, int size);
}
