package io.korion.offlinepay.application.service.settlement;

import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SettlementPolicyEvaluator {

    private final JsonService jsonService;

    public SettlementPolicyEvaluator(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public SettlementEvaluation evaluate(OfflinePaymentProof proof, CollateralLock collateral, Device device) {
        Map<String, Object> payload = extractPayload(proof);
        String network = readText(payload, "network");
        String token = readText(payload, "token");
        String uiMode = readText(payload, "uiMode");
        String connectionType = readText(payload, "connectionType");
        String paymentFlow = readText(payload, "paymentFlow");
        String ledgerExecutionMode = readText(payload, "ledgerExecutionMode");
        BigDecimal localAvailableAmount = readDecimal(payload, "availableAmount");
        Boolean senderAuthRequired = readBoolean(payload, "senderAuthRequired");
        Boolean dualAmountEntered = readBoolean(payload, "dualAmountEntered");

        if (device.status() != DeviceStatus.ACTIVE) {
            return rejected(OfflinePayReasonCode.DEVICE_NOT_ACTIVE);
        }
        if (proof.keyVersion() != device.keyVersion()) {
            return rejected(OfflinePayReasonCode.KEY_VERSION_MISMATCH);
        }
        if (proof.expiresAtMs() < Instant.now().toEpochMilli()) {
            return new SettlementEvaluation(
                    SettlementStatus.EXPIRED,
                    false,
                    OfflinePayReasonCode.PROOF_EXPIRED,
                    jsonService.write(Map.of("reasonCode", OfflinePayReasonCode.PROOF_EXPIRED)),
                    BigDecimal.ZERO,
                    "ADJUST"
            );
        }
        if (uiMode == null) {
            return rejected(OfflinePayReasonCode.PAYMENT_MODE_REQUIRED);
        }
        if ("IDLE".equalsIgnoreCase(uiMode)) {
            return rejected(OfflinePayReasonCode.IDLE_MODE_SUBMISSION_NOT_ALLOWED);
        }
        if (connectionType == null) {
            return rejected(OfflinePayReasonCode.CONNECTION_TYPE_REQUIRED);
        }
        if (paymentFlow == null) {
            return rejected(OfflinePayReasonCode.PAYMENT_FLOW_REQUIRED);
        }
        if ("FAST_PAYMENT".equalsIgnoreCase(paymentFlow) && "MANUAL_SELECTION".equalsIgnoreCase(connectionType)) {
            return rejected(OfflinePayReasonCode.FAST_PAYMENT_REQUIRES_FAST_CONTACT);
        }
        if (Boolean.TRUE.equals(dualAmountEntered)) {
            return rejected(OfflinePayReasonCode.AMOUNT_CONFLICT_DETECTED);
        }
        if (senderAuthRequired != null && !senderAuthRequired) {
            return rejected(OfflinePayReasonCode.SENDER_AUTH_REQUIRED);
        }
        if (ledgerExecutionMode != null && !"INTERNAL_LEDGER_ONLY".equalsIgnoreCase(ledgerExecutionMode)) {
            return rejected(OfflinePayReasonCode.LEDGER_EXECUTION_MODE_INVALID);
        }
        if (network == null) {
            return rejected(OfflinePayReasonCode.NETWORK_REQUIRED);
        }
        if (!"TRC-20".equalsIgnoreCase(network)) {
            return rejected(OfflinePayReasonCode.NETWORK_NOT_ALLOWED);
        }
        if (token == null) {
            return rejected(OfflinePayReasonCode.TOKEN_REQUIRED);
        }
        if (!collateral.assetCode().equalsIgnoreCase(token)) {
            return rejected(OfflinePayReasonCode.TOKEN_MISMATCH);
        }
        if (localAvailableAmount == null) {
            return rejected(OfflinePayReasonCode.LOCAL_AVAILABLE_AMOUNT_REQUIRED);
        }
        if (localAvailableAmount.compareTo(proof.amount()) < 0) {
            return rejected(OfflinePayReasonCode.LOCAL_AVAILABLE_AMOUNT_EXCEEDED);
        }
        if (collateral.remainingAmount().compareTo(proof.amount()) < 0) {
            return rejected(OfflinePayReasonCode.SERVER_AVAILABLE_AMOUNT_EXCEEDED);
        }
        return new SettlementEvaluation(
                SettlementStatus.SETTLED,
                false,
                null,
                jsonService.write(Map.of(
                        "reasonCode", OfflinePayReasonCode.SETTLED,
                        "uiMode", uiMode,
                        "connectionType", connectionType,
                        "paymentFlow", paymentFlow,
                        "voucherId", proof.voucherId()
                )),
                proof.amount(),
                "RELEASE"
        );
    }

    private SettlementEvaluation rejected(String reasonCode) {
        return new SettlementEvaluation(
                SettlementStatus.REJECTED,
                false,
                reasonCode,
                jsonService.write(Map.of("reasonCode", reasonCode)),
                BigDecimal.ZERO,
                "ADJUST"
        );
    }

    private Map<String, Object> extractPayload(OfflinePaymentProof proof) {
        var node = jsonService.readTree(proof.rawPayloadJson());
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("network", node.path("network").asText(null));
        payload.put("token", node.path("token").asText(null));
        payload.put("uiMode", node.path("uiMode").asText(null));
        payload.put("connectionType", node.path("connectionType").asText(null));
        payload.put("paymentFlow", node.path("paymentFlow").asText(null));
        payload.put("availableAmount", node.path("availableAmount").asText(null));
        payload.put("ledgerExecutionMode", node.path("ledgerExecutionMode").asText(null));
        payload.put("senderAuthRequired", node.path("senderAuthRequired").asText(null));
        payload.put("dualAmountEntered", node.path("dualAmountEntered").asText(null));
        return payload;
    }

    private String readText(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal readDecimal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean readBoolean(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }
}
