package io.korion.offlinepay.interfaces.http.factory;

import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.interfaces.http.dto.FinalizeSettlementResponse;
import io.korion.offlinepay.interfaces.http.dto.SettlementBatchDetailResponse;
import org.springframework.stereotype.Component;

@Component
public class SettlementResponseFactory {

    public SettlementBatchDetailResponse toBatchDetail(SettlementBatch batch) {
        return new SettlementBatchDetailResponse(batch.id(), batch.status().name(), batch.proofsCount());
    }

    public FinalizeSettlementResponse toFinalizeResponse(SettlementRequest settlementRequest) {
        return new FinalizeSettlementResponse(settlementRequest.id(), settlementRequest.status().name());
    }
}

