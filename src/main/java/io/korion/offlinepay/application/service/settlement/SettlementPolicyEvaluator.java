package io.korion.offlinepay.application.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.policy.DeviceTrustContract;
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
    private final OfflinePaySettlementFeeCalculator feeCalculator;

    public SettlementPolicyEvaluator(JsonService jsonService, OfflinePaySettlementFeeCalculator feeCalculator) {
        this.jsonService = jsonService;
        this.feeCalculator = feeCalculator;
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
        BigDecimal feeAmount = feeCalculator.calculateFee(collateral.assetCode(), proof.amount());
        BigDecimal settlementTotal = feeCalculator.calculateTotal(collateral.assetCode(), proof.amount());
        Boolean senderAuthRequired = readBoolean(payload, "senderAuthRequired");
        Boolean dualAmountEntered = readBoolean(payload, "dualAmountEntered");
        String deviceTrustLevel = readText(payload, "deviceTrustLevel");
        boolean trustContractMet = DeviceTrustContract.MINIMUM_ATTESTATION_VERDICT.equals(deviceTrustLevel);

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
        if (localAvailableAmount.compareTo(settlementTotal) < 0) {
            return rejected(OfflinePayReasonCode.LOCAL_AVAILABLE_AMOUNT_EXCEEDED);
        }
        if (collateral.remainingAmount().compareTo(settlementTotal) < 0) {
            return rejected(OfflinePayReasonCode.SERVER_AVAILABLE_AMOUNT_EXCEEDED);
        }
        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("reasonCode", OfflinePayReasonCode.SETTLED);
        resultJson.put("uiMode", uiMode);
        resultJson.put("connectionType", connectionType);
        resultJson.put("paymentFlow", paymentFlow);
        resultJson.put("feeAmount", feeAmount);
        resultJson.put("settlementTotal", settlementTotal);
        resultJson.put("voucherId", proof.voucherId());
        resultJson.put("deviceTrustLevel", deviceTrustLevel);
        resultJson.put("trustContractMet", trustContractMet);
        resultJson.put("contractRequirements", DeviceTrustContract.MINIMUM_ATTESTATION_VERDICT);
        return new SettlementEvaluation(
                SettlementStatus.SETTLED,
                false,
                null,
                jsonService.write(resultJson),
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
        var rawNode = jsonService.readTree(proof.rawPayloadJson());
        var canonicalNode = jsonService.readTree(proof.canonicalPayload());
        if (!rawNode.isObject() && !canonicalNode.isObject()) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("network", firstText(rawNode, canonicalNode, "network"));
        payload.put("token", firstText(rawNode, canonicalNode, "token"));
        payload.put("uiMode", firstText(rawNode, canonicalNode, "uiMode"));
        payload.put("connectionType", firstText(rawNode, canonicalNode, "connectionType"));
        payload.put("paymentFlow", firstText(rawNode, canonicalNode, "paymentFlow"));
        payload.put("availableAmount", firstText(rawNode, canonicalNode, "availableAmount"));
        payload.put("ledgerExecutionMode", firstText(rawNode, canonicalNode, "ledgerExecutionMode"));
        payload.put("senderAuthRequired", firstText(rawNode, canonicalNode, "senderAuthRequired"));
        payload.put("dualAmountEntered", firstText(rawNode, canonicalNode, "dualAmountEntered"));
        payload.put("deviceTrustLevel", firstText(rawNode, canonicalNode, "deviceTrustLevel"));
        return payload;
    }

    private String firstText(JsonNode primary, JsonNode fallback, String key) {
        String value = textOrNull(primary, key);
        return value != null ? value : textOrNull(fallback, key);
    }

    private String textOrNull(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode valueNode = node.path(key);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText(null);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
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
