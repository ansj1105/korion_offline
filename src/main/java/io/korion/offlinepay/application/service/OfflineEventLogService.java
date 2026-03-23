package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflineEventLogService {

    private final DeviceRepository deviceRepository;
    private final OfflineEventLogRepository offlineEventLogRepository;
    private final JsonService jsonService;

    public OfflineEventLogService(
            DeviceRepository deviceRepository,
            OfflineEventLogRepository offlineEventLogRepository,
            JsonService jsonService
    ) {
        this.deviceRepository = deviceRepository;
        this.offlineEventLogRepository = offlineEventLogRepository;
        this.jsonService = jsonService;
    }

    @Transactional
    public OfflineEventLog record(RecordOfflineEventCommand command) {
        deviceRepository.findByDeviceId(command.deviceId())
                .filter(device -> device.userId() == command.userId())
                .orElseThrow(() -> new IllegalArgumentException("device binding mismatch: " + command.deviceId()));

        return offlineEventLogRepository.save(
                command.userId(),
                command.deviceId(),
                command.eventType(),
                command.eventStatus(),
                command.assetCode(),
                command.networkCode(),
                command.amount(),
                command.requestId(),
                command.settlementId(),
                command.counterpartyDeviceId(),
                command.counterpartyActor(),
                command.reasonCode(),
                command.message(),
                jsonService.write(command.metadata() == null ? Map.of() : command.metadata())
        );
    }

    @Transactional(readOnly = true)
    public List<OfflineEventLog> listRecent(int size, String eventType, String eventStatus, String assetCode) {
        return offlineEventLogRepository.findRecent(
                size,
                parseEventType(eventType),
                parseEventStatus(eventStatus),
                assetCode
        );
    }

    public record RecordOfflineEventCommand(
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
            Map<String, Object> metadata
    ) {}

    private OfflineEventType parseEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        try {
            return OfflineEventType.valueOf(eventType.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private OfflineEventStatus parseEventStatus(String eventStatus) {
        if (eventStatus == null || eventStatus.isBlank()) {
            return null;
        }
        try {
            return OfflineEventStatus.valueOf(eventStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
