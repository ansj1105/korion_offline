package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProofPayloadConsistencyValidator {

    private final JsonService jsonService;

    public ProofPayloadConsistencyValidator(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public ValidationResult validate(OfflinePaymentProof proof) {
        JsonNode rawPayload = jsonService.readTree(proof.rawPayloadJson());
        JsonNode canonicalPayload = jsonService.readTree(proof.canonicalPayload());
        JsonNode spendingProof = rawPayload.path("spendingProof");

        ValidationResult requiredFieldsValidation = validateRequiredFields(proof, rawPayload, canonicalPayload, spendingProof);
        if (!requiredFieldsValidation.passed()) {
            return requiredFieldsValidation;
        }

        if (mismatch(text(rawPayload, "voucherId"), proof.voucherId()) || mismatch(text(canonicalPayload, "voucherId"), proof.voucherId())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_VOUCHER_MISMATCH, proof, "voucherId mismatch");
        }
        if (mismatch(text(rawPayload, "deviceId"), proof.senderDeviceId()) || mismatch(text(spendingProof, "deviceId"), proof.senderDeviceId())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_DEVICE_MISMATCH, proof, "sender device mismatch");
        }
        if (mismatch(text(rawPayload, "counterpartyDeviceId"), proof.receiverDeviceId()) || mismatch(text(canonicalPayload, "counterpartyDeviceId"), proof.receiverDeviceId())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_DEVICE_MISMATCH, proof, "receiver device mismatch");
        }
        if (mismatch(decimal(rawPayload, "amount"), proof.amount()) || mismatch(decimal(spendingProof, "amount"), proof.amount())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_AMOUNT_MISMATCH, proof, "amount mismatch");
        }
        if (mismatch(longValue(spendingProof, "monotonicCounter"), proof.counter())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_COUNTER_MISMATCH, proof, "counter mismatch");
        }
        if (mismatch(text(spendingProof, "nonce"), proof.nonce())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_NONCE_MISMATCH, proof, "nonce mismatch");
        }
        if (mismatch(text(spendingProof, "newStateHash"), proof.hashChainHead()) || mismatch(text(spendingProof, "prevStateHash"), proof.previousHash())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_HASH_MISMATCH, proof, "state hash mismatch");
        }
        if (mismatch(text(spendingProof, "signature"), proof.signature())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_SIGNATURE_MISMATCH, proof, "signature mismatch");
        }
        if (mismatchEpochMillis(longValue(spendingProof, "timestamp"), proof.timestampMs())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_TIMESTAMP_MISMATCH, proof, "timestamp mismatch");
        }
        if (mismatchEpochMillis(longValue(rawPayload, "expiresAt"), proof.expiresAtMs())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_EXPIRY_MISMATCH, proof, "expiry mismatch");
        }
        if (text(rawPayload, "payloadHash") != null
                && mismatch(text(rawPayload, "payloadHash"), sha256Hex(proof.canonicalPayload()))) {
            return invalid(OfflinePayReasonCode.PAYLOAD_HASH_MISMATCH, proof, "payloadHash mismatch");
        }
        return ValidationResult.success();
    }

    private ValidationResult validateRequiredFields(
            OfflinePaymentProof proof,
            JsonNode rawPayload,
            JsonNode canonicalPayload,
            JsonNode spendingProof
    ) {
        if (isBlank(text(rawPayload, "voucherId")) || isBlank(text(canonicalPayload, "voucherId"))) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "voucherId missing");
        }
        if (isBlank(text(rawPayload, "deviceId")) || isBlank(text(spendingProof, "deviceId"))) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "deviceId missing");
        }
        if (isBlank(text(rawPayload, "counterpartyDeviceId")) || isBlank(text(canonicalPayload, "counterpartyDeviceId"))) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "counterpartyDeviceId missing");
        }
        if (decimal(rawPayload, "amount") == null || decimal(spendingProof, "amount") == null) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "amount missing");
        }
        if (longValue(spendingProof, "monotonicCounter") == null) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "monotonicCounter missing");
        }
        if (isBlank(text(spendingProof, "nonce"))) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "nonce missing");
        }
        if (isBlank(text(spendingProof, "newStateHash")) || isBlank(text(spendingProof, "prevStateHash"))) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "state hash missing");
        }
        if (isBlank(text(spendingProof, "signature"))) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "signature missing");
        }
        if (longValue(spendingProof, "timestamp") == null) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "timestamp missing");
        }
        if (longValue(rawPayload, "expiresAt") == null) {
            return invalid(OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING, proof, "expiresAt missing");
        }
        return ValidationResult.success();
    }

    private ValidationResult invalid(String reasonCode, OfflinePaymentProof proof, String detail) {
        return new ValidationResult(
                false,
                reasonCode,
                jsonService.write(Map.of(
                        "voucherId", proof.voucherId(),
                        "reasonCode", reasonCode,
                        "detail", detail
                ))
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String value = child.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long longValue(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean mismatch(String payloadValue, String actualValue) {
        return payloadValue != null && actualValue != null && !payloadValue.equals(actualValue);
    }

    private boolean mismatch(BigDecimal payloadValue, BigDecimal actualValue) {
        return payloadValue != null && actualValue != null && payloadValue.compareTo(actualValue) != 0;
    }

    private boolean mismatch(Long payloadValue, long actualValue) {
        return payloadValue != null && payloadValue != actualValue;
    }

    private boolean mismatchEpochMillis(Long payloadValue, long actualValue) {
        return payloadValue != null && normalizeEpochMillis(payloadValue) != normalizeEpochMillis(actualValue);
    }

    private long normalizeEpochMillis(long value) {
        if (value > 0 && value < 10_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    public record ValidationResult(
            boolean passed,
            String reasonCode,
            String detailJson
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, null, "{}");
        }
    }
}
