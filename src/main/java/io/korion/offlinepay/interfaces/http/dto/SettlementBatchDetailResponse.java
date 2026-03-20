package io.korion.offlinepay.interfaces.http.dto;

public record SettlementBatchDetailResponse(
        String batchId,
        String status,
        int proofsCount
) {}

