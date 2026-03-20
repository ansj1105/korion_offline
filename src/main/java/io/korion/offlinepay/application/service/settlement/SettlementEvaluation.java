package io.korion.offlinepay.application.service.settlement;

import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;

public record SettlementEvaluation(
        SettlementStatus status,
        boolean conflictDetected,
        String reasonCode,
        String resultJson,
        BigDecimal settledAmount,
        String releaseAction
) {}
