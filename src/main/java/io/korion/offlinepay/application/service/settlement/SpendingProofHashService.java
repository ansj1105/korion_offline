package io.korion.offlinepay.application.service.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SpendingProofHashService {

    private static final int CANONICAL_AMOUNT_SCALE = 6;
    private static final int LEGACY_AMOUNT_SCALE = 2;

    public String computeNewStateHash(
            String previousStateHash,
            BigDecimal transactionAmount,
            long monotonicCounter,
            String deviceId,
            String nonce
    ) {
        return computeNewStateHash(previousStateHash, transactionAmount, monotonicCounter, deviceId, nonce, CANONICAL_AMOUNT_SCALE);
    }

    public List<String> computeAcceptedNewStateHashes(
            String previousStateHash,
            BigDecimal transactionAmount,
            long monotonicCounter,
            String deviceId,
            String nonce
    ) {
        LinkedHashSet<String> hashes = new LinkedHashSet<>();
        hashes.add(computeNewStateHash(previousStateHash, transactionAmount, monotonicCounter, deviceId, nonce, CANONICAL_AMOUNT_SCALE));
        if (canRepresentAtScale(transactionAmount, LEGACY_AMOUNT_SCALE)) {
            hashes.add(computeNewStateHash(previousStateHash, transactionAmount, monotonicCounter, deviceId, nonce, LEGACY_AMOUNT_SCALE));
        }
        return List.copyOf(hashes);
    }

    private String computeNewStateHash(
            String previousStateHash,
            BigDecimal transactionAmount,
            long monotonicCounter,
            String deviceId,
            String nonce,
            int amountScale
    ) {
        String normalizedAmount = transactionAmount == null
                ? scaledZeroAmount(amountScale)
                : transactionAmount.setScale(amountScale, RoundingMode.HALF_UP).toPlainString();
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

    private String scaledZeroAmount(int amountScale) {
        return BigDecimal.ZERO.setScale(amountScale, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean canRepresentAtScale(BigDecimal amount, int amountScale) {
        if (amount == null) {
            return true;
        }
        try {
            amount.setScale(amountScale, RoundingMode.UNNECESSARY);
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
