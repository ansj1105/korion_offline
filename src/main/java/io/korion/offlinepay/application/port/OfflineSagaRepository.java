package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.OfflineSaga;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import java.util.List;
import java.util.Optional;

public interface OfflineSagaRepository {

    OfflineSaga saveOrReplace(
            OfflineSagaType sagaType,
            String referenceId,
            OfflineSagaStatus status,
            String currentStep,
            String lastReasonCode,
            String payloadJson
    );

    OfflineSaga updateStatus(
            OfflineSagaType sagaType,
            String referenceId,
            OfflineSagaStatus status,
            String currentStep,
            String lastReasonCode,
            String payloadJson
    );

    Optional<OfflineSaga> findBySagaTypeAndReferenceId(OfflineSagaType sagaType, String referenceId);

    List<OfflineSaga> findRecent(int limit, OfflineSagaType sagaType, OfflineSagaStatus status);
}
