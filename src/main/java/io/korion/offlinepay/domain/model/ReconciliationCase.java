package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.time.OffsetDateTime;

public record ReconciliationCase(
        String id,
        String settlementId,
        String batchId,
        String proofId,
        String voucherId,
        String caseType,
        ReconciliationCaseStatus status,
        String reasonCode,
        String detailJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt
) {}
