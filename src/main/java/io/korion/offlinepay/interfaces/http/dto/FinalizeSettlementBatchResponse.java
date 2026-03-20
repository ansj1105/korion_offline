package io.korion.offlinepay.interfaces.http.dto;

public record FinalizeSettlementBatchResponse(
        String batchId,
        String status
) {}

