package io.korion.offlinepay.application.factory;

import io.korion.offlinepay.application.service.JsonService;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SettlementRequestFactory {

    private final JsonService jsonService;

    public SettlementRequestFactory(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public String uploadedResult() {
        return jsonService.write(Map.of("stage", "uploaded"));
    }

    public String validatingResult() {
        return jsonService.write(Map.of("stage", "validating"));
    }

    public FinalizeDecision finalizeDecision(boolean conflictDetected, String batchId) {
        return new FinalizeDecision(
                conflictDetected ? "ADJUST" : "RELEASE",
                conflictDetected ? SettlementStatus.CONFLICT : SettlementStatus.SETTLED,
                conflictDetected ? CollateralStatus.PARTIALLY_SETTLED : CollateralStatus.RELEASED,
                conflictDetected ? SettlementBatchStatus.PARTIALLY_SETTLED : SettlementBatchStatus.SETTLED,
                jsonService.write(Map.of("releaseAction", conflictDetected ? "ADJUST" : "RELEASE")),
                jsonService.write(Map.of("finalizedByBatchId", batchId))
        );
    }

    public record FinalizeDecision(
            String releaseAction,
            SettlementStatus settlementStatus,
            CollateralStatus collateralStatus,
            SettlementBatchStatus batchStatus,
            String settlementResultJson,
            String collateralMetadataJson
    ) {}
}

