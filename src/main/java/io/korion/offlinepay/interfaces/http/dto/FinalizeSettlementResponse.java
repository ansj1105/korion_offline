package io.korion.offlinepay.interfaces.http.dto;

public record FinalizeSettlementResponse(
        String settlementId,
        String status
) {}

