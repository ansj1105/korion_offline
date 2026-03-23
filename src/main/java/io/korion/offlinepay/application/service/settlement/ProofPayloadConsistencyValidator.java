package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import java.math.BigDecimal;
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
        if (mismatch(longValue(spendingProof, "timestamp"), proof.timestampMs())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_TIMESTAMP_MISMATCH, proof, "timestamp mismatch");
        }
        if (mismatch(longValue(rawPayload, "expiresAt"), proof.expiresAtMs())) {
            return invalid(OfflinePayReasonCode.PAYLOAD_EXPIRY_MISMATCH, proof, "expiry mismatch");
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
