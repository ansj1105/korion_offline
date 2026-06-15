package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.OfflineEventLogRepository;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import io.korion.offlinepay.interfaces.http.dto.SubmitClientEventBatchRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientEventBatchService {

    private final DeviceRepository deviceRepository;
    private final OfflineEventLogRepository eventLogRepository;
    private final OfflineEventLogService eventLogService;
    private final SettlementApplicationService settlementApplicationService;

    public ClientEventBatchService(
            DeviceRepository deviceRepository,
            OfflineEventLogRepository eventLogRepository,
            OfflineEventLogService eventLogService,
            SettlementApplicationService settlementApplicationService
    ) {
        this.deviceRepository = deviceRepository;
        this.eventLogRepository = eventLogRepository;
        this.eventLogService = eventLogService;
        this.settlementApplicationService = settlementApplicationService;
    }

    @Transactional
    public ClientEventBatchResponse submit(SubmitClientEventBatchRequest request) {
        Device device = deviceRepository.findByDeviceId(request.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("device not registered: " + request.deviceId()));
        List<ClientEventResult> results = new ArrayList<>();
        Set<String> seenEventIds = new LinkedHashSet<>();
        int accepted = 0;
        int duplicated = 0;
        int failed = 0;

        for (SubmitClientEventBatchRequest.ClientEventRequest event : request.events()) {
            String eventId = event.eventId() == null ? "" : event.eventId().trim();
            try {
                if (eventId.isBlank()) {
                    throw new IllegalArgumentException("eventId is required");
                }
                if (!seenEventIds.add(eventId) || eventLogRepository.existsByClientEventId(eventId)) {
                    duplicated++;
                    results.add(new ClientEventResult(eventId, "DUPLICATE", "DUPLICATE_EVENT", null, null, null));
                    continue;
                }
                EventProcessingOutcome outcome = processEvent(device, request, event);
                accepted++;
                results.add(new ClientEventResult(
                        eventId,
                        "ACCEPTED",
                        null,
                        outcome.eventLogId(),
                        outcome.settlementBatchId(),
                        outcome.localEvidenceStatus()
                ));
            } catch (RuntimeException exception) {
                failed++;
                results.add(new ClientEventResult(
                        eventId,
                        "FAILED",
                        normalizeReason(exception),
                        null,
                        null,
                        null
                ));
            }
        }

        return new ClientEventBatchResponse(
                request.deviceId(),
                device.userId(),
                request.events().size(),
                accepted,
                duplicated,
                failed,
                OffsetDateTime.now().toString(),
                results
        );
    }

    private EventProcessingOutcome processEvent(
            Device device,
            SubmitClientEventBatchRequest request,
            SubmitClientEventBatchRequest.ClientEventRequest event
    ) {
        String assetCode = firstNonBlank(event.assetCode(), request.assetCode(), "KORI").toUpperCase(Locale.ROOT);
        String networkCode = firstNonBlank(event.networkCode(), "mainnet");
        OfflineEventType eventType = OfflineEventType.valueOf(event.type().trim().toUpperCase(Locale.ROOT));
        OfflineEventStatus eventStatus = parseStatus(event.status());
        validateReasonPolicy(eventType, eventStatus, event.reasonCode());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("eventId", event.eventId().trim());
        metadata.put("direction", firstNonBlank(event.direction(), ""));
        metadata.put("clientStatus", firstNonBlank(event.status(), ""));
        metadata.put("clientType", event.type().trim());
        if (event.payload() != null && !event.payload().isEmpty()) {
            metadata.put("payload", event.payload());
        }

        String settlementBatchId = null;
        String localEvidenceStatus = null;
        if (event.proof() != null) {
            var batch = settlementApplicationService.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                    uploaderType(event),
                    firstNonBlank(event.uploaderDeviceId(), request.deviceId()),
                    "client-event:" + event.eventId().trim(),
                    List.of(toProofSubmission(event.proof())),
                    "CLIENT_EVENT_BATCH"
            ));
            settlementBatchId = batch.id();
        }
        if (event.evidence() != null) {
            var result = settlementApplicationService.ingestLocalEvidence(new SettlementApplicationService.LocalEvidenceIngestCommand(
                    uploaderType(event),
                    firstNonBlank(event.uploaderDeviceId(), request.deviceId()),
                    "client-event:" + event.eventId().trim(),
                    List.of(toLocalEvidenceSubmission(event.evidence()))
            ));
            localEvidenceStatus = "requested=" + result.requested()
                    + ",stored=" + result.stored()
                    + ",matched=" + result.matched()
                    + ",awaitingCarrier=" + result.awaitingCarrier();
        }

        var eventLog = eventLogService.record(new OfflineEventLogService.RecordOfflineEventCommand(
                device.userId(),
                device.deviceId(),
                eventType,
                eventStatus,
                assetCode,
                networkCode,
                event.amount() == null ? BigDecimal.ZERO : event.amount(),
                blankToNull(firstNonBlank(event.requestId(), event.sessionId())),
                blankToNull(event.settlementId()),
                blankToNull(event.counterpartyDeviceId()),
                blankToNull(event.counterpartyActor()),
                blankToNull(event.reasonCode()),
                blankToNull(event.message()),
                metadata
        ));

        return new EventProcessingOutcome(eventLog.id(), settlementBatchId, localEvidenceStatus);
    }

    private SettlementApplicationService.ProofSubmission toProofSubmission(
            SubmitClientEventBatchRequest.ProofEventRequest proof
    ) {
        return new SettlementApplicationService.ProofSubmission(
                proof.voucherId(),
                proof.collateralId(),
                proof.issuerDeviceId(),
                proof.receiverDeviceId(),
                proof.keyVersion().intValue(),
                proof.policyVersion().intValue(),
                proof.counter(),
                proof.nonce(),
                proof.newHash(),
                proof.prevHash(),
                proof.signature(),
                proof.amount(),
                normalizeEpochMillis(proof.timestamp()),
                normalizeEpochMillis(proof.expiresAt()),
                proof.canonicalPayload(),
                proof.payload()
        );
    }

    private SettlementApplicationService.LocalEvidenceSubmission toLocalEvidenceSubmission(
            SubmitClientEventBatchRequest.EvidenceEventRequest evidence
    ) {
        return new SettlementApplicationService.LocalEvidenceSubmission(
                evidence.voucherId(),
                evidence.sessionId(),
                evidence.direction(),
                evidence.senderDeviceId(),
                evidence.receiverDeviceId(),
                evidence.amount(),
                evidence.counter(),
                evidence.prevHash(),
                evidence.newHash(),
                evidence.nonce(),
                evidence.signature(),
                evidence.canonicalPayload(),
                evidence.merchantId(),
                evidence.partnerId(),
                evidence.leaderId(),
                evidence.countryCode(),
                evidence.storeId(),
                evidence.orderId(),
                evidence.paymentIntentId(),
                evidence.invoiceId(),
                evidence.fiatAmount(),
                evidence.fiatCurrency(),
                evidence.exchangeRate(),
                evidence.rateTimestamp(),
                evidence.schemaVersion(),
                evidence.protocolVersion(),
                evidence.hashAlgorithm(),
                evidence.signatureAlgorithm(),
                evidence.keyId(),
                evidence.publicKeyFingerprint(),
                evidence.appVersion(),
                evidence.deviceAttestationId(),
                evidence.deviceAttestationVerdict(),
                evidence.serverVerifiedTrustLevel(),
                evidence.serverAttestationVerifiedAt(),
                evidence.transportSessionHash(),
                evidence.transportTranscriptSource(),
                evidence.transportTranscript(),
                evidence.transportTranscriptEncoding(),
                evidence.payload()
        );
    }

    private SettlementApplicationService.UploaderType uploaderType(
            SubmitClientEventBatchRequest.ClientEventRequest event
    ) {
        String explicit = firstNonBlank(event.uploaderType(), "");
        if (!explicit.isBlank()) {
            return SettlementApplicationService.UploaderType.valueOf(explicit.toUpperCase(Locale.ROOT));
        }
        String direction = firstNonBlank(event.direction(), "").toUpperCase(Locale.ROOT);
        return "RECEIVE".equals(direction)
                ? SettlementApplicationService.UploaderType.RECEIVER
                : SettlementApplicationService.UploaderType.SENDER;
    }

    private OfflineEventStatus parseStatus(String status) {
        String normalized = firstNonBlank(status, "PENDING").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACKNOWLEDGED", "SUCCESS", "SUCCEEDED", "UPLOADED", "SERVER_FINAL_SUCCESS" ->
                    OfflineEventStatus.ACKNOWLEDGED;
            case "FAILED", "SERVER_FINAL_FAILED", "REJECTED", "EXPIRED" -> OfflineEventStatus.FAILED;
            default -> OfflineEventStatus.PENDING;
        };
    }

    private void validateReasonPolicy(OfflineEventType eventType, OfflineEventStatus eventStatus, String reasonCode) {
        boolean failedStatus = eventStatus == OfflineEventStatus.FAILED;
        boolean failedType = switch (eventType) {
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
                    LEDGER_SYNC_FAIL,
                    HISTORY_SYNC_FAIL,
                    LEDGER_CIRCUIT_OPEN,
                    HISTORY_CIRCUIT_OPEN,
                    SERVER_VALIDATION_FAIL,
                    SETTLEMENT_FAIL,
                    SYNC_FAILED,
                    SETTLEMENT_FAILED,
                    TRANSPORT_FAILED -> true;
            default -> false;
        };
        if ((failedStatus || failedType) && (reasonCode == null || reasonCode.isBlank())) {
            throw new IllegalArgumentException("reasonCode is required for failed client event");
        }
    }

    private long normalizeEpochMillis(long value) {
        if (value > 0 && value < 10_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }

    private String normalizeReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ClientEventBatchResponse(
            String deviceId,
            long userId,
            int requested,
            int accepted,
            int duplicated,
            int failed,
            String receivedAt,
            List<ClientEventResult> results
    ) {}

    public record ClientEventResult(
            String eventId,
            String status,
            String reasonCode,
            String eventLogId,
            String settlementBatchId,
            String localEvidenceStatus
    ) {}

    private record EventProcessingOutcome(
            String eventLogId,
            String settlementBatchId,
            String localEvidenceStatus
    ) {}
}
