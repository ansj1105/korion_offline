package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

public record RecordOfflineEventRequest(
        @NotNull Long userId,
        @NotBlank String deviceId,
        @NotBlank String eventType,
        @NotBlank String eventStatus,
        String assetCode,
        String networkCode,
        BigDecimal amount,
        String requestId,
        String settlementId,
        String counterpartyDeviceId,
        String counterpartyActor,
        String reasonCode,
        String message,
        Map<String, Object> metadata
) {}
