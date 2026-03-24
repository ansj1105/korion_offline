package io.korion.offlinepay.domain.model;

import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import java.time.OffsetDateTime;

public record OfflineSaga(
        String id,
        OfflineSagaType sagaType,
        String referenceId,
        OfflineSagaStatus status,
        String currentStep,
        String lastReasonCode,
        String payloadJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
