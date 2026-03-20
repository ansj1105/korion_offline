package io.korion.offlinepay.domain.model;

import java.time.OffsetDateTime;

public record SettlementStatusMetric(
        OffsetDateTime bucketAt,
        String status,
        long count
) {}
