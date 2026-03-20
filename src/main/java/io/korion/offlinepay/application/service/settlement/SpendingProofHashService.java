package io.korion.offlinepay.application.service.settlement;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class SpendingProofHashService {

    public String computeNewStateHash(
            String previousStateHash,
            BigDecimal transactionAmount,
            long monotonicCounter,
            String deviceId,
            String nonce
    ) {
        String normalizedAmount = transactionAmount == null
                ? "0"
                : transactionAmount.stripTrailingZeros().toPlainString();
        String payload = String.join(
                "|",
                blankSafe(previousStateHash),
                normalizedAmount,
                String.valueOf(monotonicCounter),
                blankSafe(deviceId),
                blankSafe(nonce)
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
