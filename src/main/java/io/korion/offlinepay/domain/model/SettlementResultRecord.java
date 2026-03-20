package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SettlementResultRecord(
        String id,
        String settlementId,
        String batchId,
        String voucherId,
        String collateralId,
        String senderDeviceId,
        String receiverDeviceId,
        SettlementStatus status,
        String reasonCode,
        String detailJson,
        BigDecimal settledAmount,
        OffsetDateTime processedAt,
        OffsetDateTime createdAt
) {}
