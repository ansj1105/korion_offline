package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.SettlementConflict;
import io.korion.offlinepay.domain.model.SettlementConflictMetric;
import java.util.List;

public interface SettlementConflictRepository {

    SettlementConflict save(
            String settlementId,
            String voucherId,
            String collateralId,
            String deviceId,
            String conflictType,
            String severity,
            String detailJson
    );

    List<SettlementConflict> findRecent(
            String status,
            String conflictType,
            String collateralId,
            String deviceId,
            String networkScope,
            int size
    );

    List<SettlementConflictMetric> summarizeByHour(int hours, String networkScope);
}
