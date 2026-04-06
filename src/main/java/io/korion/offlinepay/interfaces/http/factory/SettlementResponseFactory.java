package io.korion.offlinepay.interfaces.http.factory;

import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.interfaces.http.dto.FinalizeSettlementResponse;
import io.korion.offlinepay.interfaces.http.dto.SettlementBatchDetailResponse;
import io.korion.offlinepay.interfaces.http.dto.SettlementRequestDetailResponse;
import org.springframework.stereotype.Component;

@Component
public class SettlementResponseFactory {

    public SettlementBatchDetailResponse toBatchDetail(SettlementBatch batch) {
        return new SettlementBatchDetailResponse(batch.id(), batch.status().name(), batch.proofsCount());
    }

    public FinalizeSettlementResponse toFinalizeResponse(SettlementRequest settlementRequest) {
        return new FinalizeSettlementResponse(settlementRequest.id(), settlementRequest.status().name());
    }

    public SettlementRequestDetailResponse toDetailResponse(SettlementRequest settlementRequest) {
        return new SettlementRequestDetailResponse(
                settlementRequest.id(),
                settlementRequest.batchId(),
                settlementRequest.status().name(),
                settlementRequest.reasonCode(),
                settlementRequest.conflictDetected(),
                settlementRequest.updatedAt() == null ? null : settlementRequest.updatedAt().toString()
        );
    }
}
