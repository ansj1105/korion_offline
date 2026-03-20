package io.korion.offlinepay.application.service.settlement;

import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.time.Instant;
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
        BigDecimal localAvailableAmount = readDecimal(payload, "availableAmount");

        if (device.status() != DeviceStatus.ACTIVE) {
            return rejected("DEVICE_NOT_ACTIVE");
        }
        if (proof.keyVersion() != device.keyVersion()) {
            return rejected("KEY_VERSION_MISMATCH");
        }
        if (proof.expiresAtMs() < Instant.now().toEpochMilli()) {
            return new SettlementEvaluation(
                    SettlementStatus.EXPIRED,
                    false,
                    "PROOF_EXPIRED",
                    jsonService.write(Map.of("reasonCode", "PROOF_EXPIRED")),
                    BigDecimal.ZERO,
                    "ADJUST"
            );
        }
        if (network == null) {
            return rejected("NETWORK_REQUIRED");
        }
        if (!"TRC-20".equalsIgnoreCase(network)) {
            return rejected("NETWORK_NOT_ALLOWED");
        }
        if (token == null) {
            return rejected("TOKEN_REQUIRED");
        }
        if (!collateral.assetCode().equalsIgnoreCase(token)) {
            return rejected("TOKEN_MISMATCH");
        }
        if (localAvailableAmount == null) {
            return rejected("LOCAL_AVAILABLE_AMOUNT_REQUIRED");
        }
        if (localAvailableAmount.compareTo(proof.amount()) < 0) {
            return rejected("LOCAL_AVAILABLE_AMOUNT_EXCEEDED");
        }
        if (collateral.remainingAmount().compareTo(proof.amount()) < 0) {
            return rejected("SERVER_AVAILABLE_AMOUNT_EXCEEDED");
        }
        return new SettlementEvaluation(
                SettlementStatus.SETTLED,
                false,
                null,
                jsonService.write(Map.of(
                        "reasonCode", "SETTLED",
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
        return Map.of(
                "network", node.path("network").asText(null),
                "token", node.path("token").asText(null),
                "availableAmount", node.path("availableAmount").asText(null)
        );
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
}
