package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflinePaymentProof;
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
            String rawPayloadJson
    );

    java.util.Optional<OfflinePaymentProof> findById(String proofId);

    java.util.Optional<OfflinePaymentProof> findByVoucherId(String voucherId);

    java.util.Optional<OfflinePaymentProof> findBySenderNonce(String senderDeviceId, String nonce);

    java.util.List<OfflinePaymentProof> findByCollateralId(String collateralId);
}
