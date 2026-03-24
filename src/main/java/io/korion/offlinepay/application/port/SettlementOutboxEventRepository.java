package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.SettlementOutboxEvent;
import java.util.List;

public interface SettlementOutboxEventRepository {

    List<SettlementOutboxEvent> findRecent(int limit, String eventType, String status);

    long countByStatus(String status);

    long countByEventType(String eventType);
}
