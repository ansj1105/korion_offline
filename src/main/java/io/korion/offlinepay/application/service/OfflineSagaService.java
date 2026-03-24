package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.OfflineSagaRepository;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OfflineSagaService {

    private final OfflineSagaRepository offlineSagaRepository;
    private final JsonService jsonService;

    public OfflineSagaService(OfflineSagaRepository offlineSagaRepository, JsonService jsonService) {
        this.offlineSagaRepository = offlineSagaRepository;
        this.jsonService = jsonService;
    }

    public void start(OfflineSagaType sagaType, String referenceId, String currentStep, Map<String, Object> payload) {
        offlineSagaRepository.saveOrReplace(
                sagaType,
                referenceId,
                OfflineSagaStatus.ACCEPTED,
                currentStep,
                null,
                jsonService.write(payload)
        );
    }

    public void markProcessing(OfflineSagaType sagaType, String referenceId, String currentStep, Map<String, Object> payload) {
        offlineSagaRepository.updateStatus(
                sagaType,
                referenceId,
                OfflineSagaStatus.PROCESSING,
                currentStep,
                null,
                jsonService.write(payload)
        );
    }

    public void markPartiallyApplied(OfflineSagaType sagaType, String referenceId, String currentStep, Map<String, Object> payload) {
        offlineSagaRepository.updateStatus(
                sagaType,
                referenceId,
                OfflineSagaStatus.PARTIALLY_APPLIED,
                currentStep,
                null,
                jsonService.write(payload)
        );
    }

    public void markCompleted(OfflineSagaType sagaType, String referenceId, String currentStep, Map<String, Object> payload) {
        offlineSagaRepository.updateStatus(
                sagaType,
                referenceId,
                OfflineSagaStatus.COMPLETED,
                currentStep,
                null,
                jsonService.write(payload)
        );
    }

    public void markFailed(OfflineSagaType sagaType, String referenceId, String currentStep, String reasonCode, Map<String, Object> payload) {
        offlineSagaRepository.updateStatus(
                sagaType,
                referenceId,
                OfflineSagaStatus.FAILED,
                currentStep,
                reasonCode,
                jsonService.write(payload)
        );
    }

    public void markDeadLettered(OfflineSagaType sagaType, String referenceId, String currentStep, String reasonCode, Map<String, Object> payload) {
        offlineSagaRepository.updateStatus(
                sagaType,
                referenceId,
                OfflineSagaStatus.DEAD_LETTERED,
                currentStep,
                reasonCode,
                jsonService.write(payload)
        );
    }

    public void markCompensationRequired(OfflineSagaType sagaType, String referenceId, String currentStep, String reasonCode, Map<String, Object> payload) {
        offlineSagaRepository.updateStatus(
                sagaType,
                referenceId,
                OfflineSagaStatus.COMPENSATION_REQUIRED,
                currentStep,
                reasonCode,
                jsonService.write(payload)
        );
    }

    public void markCompensating(OfflineSagaType sagaType, String referenceId, String currentStep, Map<String, Object> payload) {
        offlineSagaRepository.updateStatus(
                sagaType,
                referenceId,
                OfflineSagaStatus.COMPENSATING,
                currentStep,
                null,
                jsonService.write(payload)
        );
    }

    public void markCompensated(OfflineSagaType sagaType, String referenceId, String currentStep, Map<String, Object> payload) {
        offlineSagaRepository.updateStatus(
                sagaType,
                referenceId,
                OfflineSagaStatus.COMPENSATED,
                currentStep,
                null,
                jsonService.write(payload)
        );
    }
}
