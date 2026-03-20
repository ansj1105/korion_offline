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
        if (device.status() != DeviceStatus.ACTIVE) {
            return new SettlementEvaluation(
                    SettlementStatus.REJECTED,
                    false,
                    "DEVICE_NOT_ACTIVE",
                    jsonService.write(Map.of("reasonCode", "DEVICE_NOT_ACTIVE")),
                    BigDecimal.ZERO,
                    "ADJUST"
            );
        }
        if (proof.keyVersion() != device.keyVersion()) {
            return new SettlementEvaluation(
                    SettlementStatus.REJECTED,
                    false,
                    "KEY_VERSION_MISMATCH",
                    jsonService.write(Map.of("reasonCode", "KEY_VERSION_MISMATCH")),
                    BigDecimal.ZERO,
                    "ADJUST"
            );
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
}
