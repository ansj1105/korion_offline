package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementResultRecord;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.util.List;

public interface SettlementResultRepository {

    boolean existsByVoucherId(String voucherId);

    boolean existsByVoucherIdExcludingSettlementId(String voucherId, String settlementId);

    SettlementResultRecord save(
            String settlementId,
            String batchId,
            OfflinePaymentProof proof,
            SettlementStatus status,
            String reasonCode,
            String detailJson,
            BigDecimal settledAmount
    );

    List<SettlementResultRecord> findByBatchId(String batchId);
}
