package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import java.math.BigDecimal;
import java.util.List;

public interface OfflineEventLogRepository {

    OfflineEventLog save(
            long userId,
            String deviceId,
            OfflineEventType eventType,
            OfflineEventStatus eventStatus,
            String assetCode,
            String networkCode,
            BigDecimal amount,
            String requestId,
            String settlementId,
            String counterpartyDeviceId,
            String counterpartyActor,
            String reasonCode,
            String message,
            String metadataJson
    );

    List<OfflineEventLog> findRecent(
            int size,
            OfflineEventType eventType,
            OfflineEventStatus eventStatus,
            String assetCode
    );
}
