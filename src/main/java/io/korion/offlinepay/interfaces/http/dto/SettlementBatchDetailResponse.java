package io.korion.offlinepay.interfaces.http.dto;

import java.util.List;

public record SettlementBatchDetailResponse(
        String batchId,
        String status,
        int proofsCount,
        String triggerMode,
        List<String> requestIds,
        String idempotencyKey,
        int acceptedCount,
        boolean asyncProcessing,
        String serverWorkflowStage,
        String settlementWorkflowStage
) {}
