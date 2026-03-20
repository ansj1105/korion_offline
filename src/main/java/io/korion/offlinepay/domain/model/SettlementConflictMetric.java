package io.korion.offlinepay.domain.model;

import java.time.OffsetDateTime;

public record SettlementConflictMetric(
        OffsetDateTime bucketAt,
        long count
) {}
