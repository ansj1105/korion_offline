package io.korion.offlinepay.application.service.settlement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class ProofFingerprintService {

    public String computeFingerprint(
            String settlementId,
            String batchId,
            String collateralId,
            String proofId,
            String deviceId,
            String newStateHash,
            String prevStateHash,
            long monotonicCounter,
            String nonce,
            String signature
    ) {
        try {
            String payload = String.join(
                    "|",
                    safe(settlementId),
                    safe(batchId),
                    safe(collateralId),
                    safe(proofId),
                    safe(deviceId),
                    safe(newStateHash),
                    safe(prevStateHash),
                    String.valueOf(monotonicCounter),
                    safe(nonce),
                    safe(signature)
            );
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to compute proof fingerprint", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
