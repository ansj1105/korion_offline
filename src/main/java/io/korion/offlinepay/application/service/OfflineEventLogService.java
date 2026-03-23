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
        validateReasonPolicy(command);

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

    private void validateReasonPolicy(RecordOfflineEventCommand command) {
        boolean failedStatus = command.eventStatus() == OfflineEventStatus.FAILED;
        boolean failedType = switch (command.eventType()) {
            case NFC_CONNECT_FAIL,
                    BLE_SCAN_FAIL,
                    BLE_PAIR_FAIL,
                    QR_PARSE_FAIL,
                    AUTH_BIOMETRIC_FAIL,
                    AUTH_PIN_FAIL,
                    AUTH_CANCELLED,
                    PROOF_NOT_FOUND,
                    PROOF_EXPIRED,
                    PROOF_TAMPERED,
                    PAYLOAD_BUILD_FAIL,
                    SEND_TIMEOUT,
                    SEND_INTERRUPTED,
                    RECEIVE_REJECTED,
                    LOCAL_QUEUE_SAVE_FAIL,
                    BATCH_SYNC_FAIL,
                    SERVER_VALIDATION_FAIL,
                    SETTLEMENT_FAIL,
                    SYNC_FAILED,
                    SETTLEMENT_FAILED,
                    TRANSPORT_FAILED -> true;
            default -> false;
        };
        if ((failedStatus || failedType) && (command.reasonCode() == null || command.reasonCode().isBlank())) {
            throw new IllegalArgumentException("reasonCode is required for failed offline events");
        }
    }
}
