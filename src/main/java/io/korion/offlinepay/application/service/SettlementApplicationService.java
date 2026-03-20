package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.factory.SettlementBatchFactory;
import io.korion.offlinepay.application.factory.SettlementRequestFactory;
import io.korion.offlinepay.application.factory.SettlementStreamEventFactory;
import io.korion.offlinepay.application.factory.SettlementSyncCommandFactory;
import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.port.SettlementBatchRepository;
import io.korion.offlinepay.application.port.SettlementConflictRepository;
import io.korion.offlinepay.application.port.SettlementRepository;
import io.korion.offlinepay.application.port.SettlementResultRepository;
import io.korion.offlinepay.application.service.settlement.ChainValidationResult;
import io.korion.offlinepay.application.service.settlement.ConflictDetectionResult;
import io.korion.offlinepay.application.service.settlement.ProofChainValidator;
import io.korion.offlinepay.application.service.settlement.ProofConflictDetector;
import io.korion.offlinepay.application.service.settlement.ProofSchemaValidator;
import io.korion.offlinepay.application.service.settlement.SettlementEvaluation;
import io.korion.offlinepay.application.service.settlement.SettlementPolicyEvaluator;
import io.korion.offlinepay.application.service.settlement.DeviceSignatureVerificationService;
import io.korion.offlinepay.application.service.settlement.DeviceBindingVerificationService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementApplicationService {

    private final CollateralRepository collateralRepository;
    private final DeviceRepository deviceRepository;
    private final OfflinePaymentProofRepository proofRepository;
    private final SettlementBatchRepository batchRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementResultRepository settlementResultRepository;
    private final SettlementConflictRepository settlementConflictRepository;
    private final SettlementBatchEventBus eventBus;
    private final CoinManageSettlementPort coinManageSettlementPort;
    private final FoxCoinHistoryPort foxCoinHistoryPort;
    private final JsonService jsonService;
    private final SettlementBatchFactory settlementBatchFactory;
    private final SettlementRequestFactory settlementRequestFactory;
    private final SettlementStreamEventFactory settlementStreamEventFactory;
    private final SettlementSyncCommandFactory settlementSyncCommandFactory;
    private final ProofSchemaValidator proofSchemaValidator;
    private final ProofConflictDetector proofConflictDetector;
    private final ProofChainValidator proofChainValidator;
    private final SettlementPolicyEvaluator settlementPolicyEvaluator;
    private final DeviceSignatureVerificationService deviceSignatureVerificationService;
    private final DeviceBindingVerificationService deviceBindingVerificationService;

    public SettlementApplicationService(
            CollateralRepository collateralRepository,
            DeviceRepository deviceRepository,
            OfflinePaymentProofRepository proofRepository,
            SettlementBatchRepository batchRepository,
            SettlementRepository settlementRepository,
            SettlementResultRepository settlementResultRepository,
            SettlementConflictRepository settlementConflictRepository,
            SettlementBatchEventBus eventBus,
            CoinManageSettlementPort coinManageSettlementPort,
            FoxCoinHistoryPort foxCoinHistoryPort,
            JsonService jsonService,
            SettlementBatchFactory settlementBatchFactory,
            SettlementRequestFactory settlementRequestFactory,
            SettlementStreamEventFactory settlementStreamEventFactory,
            SettlementSyncCommandFactory settlementSyncCommandFactory,
            ProofSchemaValidator proofSchemaValidator,
            ProofConflictDetector proofConflictDetector,
            ProofChainValidator proofChainValidator,
            SettlementPolicyEvaluator settlementPolicyEvaluator,
            DeviceSignatureVerificationService deviceSignatureVerificationService,
            DeviceBindingVerificationService deviceBindingVerificationService
    ) {
        this.collateralRepository = collateralRepository;
        this.deviceRepository = deviceRepository;
        this.proofRepository = proofRepository;
        this.batchRepository = batchRepository;
        this.settlementRepository = settlementRepository;
        this.settlementResultRepository = settlementResultRepository;
        this.settlementConflictRepository = settlementConflictRepository;
        this.eventBus = eventBus;
        this.coinManageSettlementPort = coinManageSettlementPort;
        this.foxCoinHistoryPort = foxCoinHistoryPort;
        this.jsonService = jsonService;
        this.settlementBatchFactory = settlementBatchFactory;
        this.settlementRequestFactory = settlementRequestFactory;
        this.settlementStreamEventFactory = settlementStreamEventFactory;
        this.settlementSyncCommandFactory = settlementSyncCommandFactory;
        this.proofSchemaValidator = proofSchemaValidator;
        this.proofConflictDetector = proofConflictDetector;
        this.proofChainValidator = proofChainValidator;
        this.settlementPolicyEvaluator = settlementPolicyEvaluator;
        this.deviceSignatureVerificationService = deviceSignatureVerificationService;
        this.deviceBindingVerificationService = deviceBindingVerificationService;
    }

    @Transactional
    public SettlementBatch submitBatch(SubmitSettlementBatchCommand command) {
        return batchRepository.findByIdempotencyKey(command.idempotencyKey())
                .orElseGet(() -> createBatch(command));
    }

    private SettlementBatch createBatch(SubmitSettlementBatchCommand command) {
        SettlementBatchFactory.SettlementBatchDraft batchDraft = settlementBatchFactory.createDraft(command);
        SettlementBatch batch = batchRepository.save(
                batchDraft.sourceDeviceId(),
                batchDraft.idempotencyKey(),
                batchDraft.status(),
                batchDraft.proofsCount(),
                batchDraft.summaryJson()
        );

        List<String> requestIds = new ArrayList<>();
        for (ProofSubmission submission : command.proofs()) {
            CollateralLock collateral = collateralRepository.findById(submission.collateralId())
                    .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + submission.collateralId()));

            OfflinePaymentProof proof = proofRepository.save(
                    batch.id(),
                    submission.voucherId(),
                    submission.collateralId(),
                    submission.senderDeviceId(),
                    submission.receiverDeviceId(),
                    submission.keyVersion(),
                    submission.policyVersion(),
                    submission.counter(),
                    submission.nonce(),
                    submission.hashChainHead(),
                    submission.previousHash(),
                    submission.signature(),
                    submission.amount(),
                    submission.timestampMs(),
                    submission.expiresAtMs(),
                    submission.canonicalPayload(),
                    command.uploaderType().name(),
                    jsonService.write(submission.payload())
            );

            SettlementRequest request = settlementRepository.save(
                    batch.id(),
                    collateral.id(),
                    proof.id(),
                    SettlementStatus.PENDING,
                    false,
                    settlementRequestFactory.uploadedResult()
            );
            requestIds.add(request.id());
        }

        batchRepository.updateStatus(
                batch.id(),
                SettlementBatchStatus.UPLOADED,
                settlementBatchFactory.uploadedSummary(requestIds)
        );
        SettlementStreamEventFactory.RequestedBatchEvent requestedBatchEvent = settlementStreamEventFactory
                .requestedBatchEvent(batch.id(), command.uploaderType().name(), command.uploaderDeviceId());
        eventBus.publishBatchRequested(
                requestedBatchEvent.batchId(),
                requestedBatchEvent.uploaderType(),
                requestedBatchEvent.uploaderDeviceId(),
                requestedBatchEvent.requestedAt()
        );
        return batchRepository.findById(batch.id()).orElseThrow();
    }

    @Transactional
    public void markBatchValidating(String batchId) {
        batchRepository.updateStatus(
                batchId,
                SettlementBatchStatus.VALIDATING,
                settlementBatchFactory.validatingSummary()
        );
        for (SettlementRequest request : settlementRepository.findByBatchId(batchId)) {
            settlementRepository.update(
                    request.id(),
                    SettlementStatus.VALIDATING,
                    false,
                    settlementRequestFactory.validatingResult()
            );
        }
    }

    @Transactional
    public SettlementBatch finalizeBatch(String batchId) {
        SettlementBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("settlement batch not found: " + batchId));
        List<SettlementRequest> requests = settlementRepository.findByBatchId(batchId);
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("settlement batch has no requests: " + batchId);
        }

        int settledCount = 0;
        int failedCount = 0;
        boolean hasConflict = false;
        for (SettlementRequest request : requests) {
            SettlementEvaluation evaluation = processSettlementRequest(request);
            if (evaluation.status() == SettlementStatus.SETTLED) {
                settledCount++;
            } else {
                failedCount++;
            }
            hasConflict = hasConflict || evaluation.conflictDetected();
        }

        SettlementBatchStatus batchStatus = resolveBatchStatus(settledCount, failedCount, hasConflict);
        batchRepository.updateStatus(
                batch.id(),
                batchStatus,
                jsonService.write(Map.of(
                        "acceptedCount", settledCount,
                        "failedCount", failedCount,
                        "hasConflict", hasConflict,
                        "finalizedAt", OffsetDateTime.now().toString()
                ))
        );
        SettlementStreamEventFactory.BatchResultEvent batchResultEvent = settlementStreamEventFactory
                .batchResultEvent(batch.id(), batchStatus, settledCount, failedCount);
        eventBus.publishBatchResult(
                batchResultEvent.batchId(),
                batchResultEvent.status(),
                batchResultEvent.settledCount(),
                batchResultEvent.failedCount(),
                batchResultEvent.processedAt()
        );
        return batchRepository.findById(batch.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public SettlementBatch getBatch(String batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("settlement batch not found: " + batchId));
    }

    @Transactional
    public SettlementRequest finalizeSettlement(String settlementId) {
        SettlementRequest request = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("settlement not found: " + settlementId));
        processSettlementRequest(request);
        return settlementRepository.findById(settlementId).orElseThrow();
    }

    @Transactional
    public BatchFailureOutcome recordBatchProcessingFailure(String batchId, String errorMessage, int maxAttempts) {
        SettlementBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("settlement batch not found: " + batchId));
        int attemptCount = currentAttemptCount(batch.summaryJson()) + 1;
        boolean deadLettered = attemptCount >= maxAttempts;

        batchRepository.updateStatus(
                batch.id(),
                deadLettered ? SettlementBatchStatus.FAILED : batch.status(),
                deadLettered
                        ? settlementBatchFactory.deadLetterSummary(attemptCount, errorMessage)
                        : settlementBatchFactory.failureSummary(attemptCount, errorMessage)
        );
        return new BatchFailureOutcome(batch.id(), attemptCount, deadLettered);
    }

    private SettlementEvaluation processSettlementRequest(SettlementRequest request) {
        OfflinePaymentProof proof = proofRepository.findById(request.proofId())
                .orElseThrow(() -> new IllegalArgumentException("proof not found: " + request.proofId()));
        CollateralLock collateral = collateralRepository.findById(request.collateralId())
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + request.collateralId()));

        SettlementEvaluation evaluation = evaluateProof(proof, collateral);
        if (evaluation.status() == SettlementStatus.SETTLED) {
            collateralRepository.deductRemainingAmount(collateral.id(), proof.amount());
        }

        settlementRepository.update(
                request.id(),
                evaluation.status(),
                evaluation.conflictDetected(),
                evaluation.resultJson()
        );
        settlementResultRepository.save(
                request.id(),
                request.batchId(),
                proof,
                evaluation.status(),
                evaluation.reasonCode(),
                evaluation.resultJson(),
                evaluation.settledAmount()
        );
        collateralRepository.updateStatus(
                collateral.id(),
                resolveCollateralStatus(collateral, proof, evaluation),
                jsonService.write(Map.of(
                        "lastSettlementId", request.id(),
                        "lastVoucherId", proof.voucherId(),
                        "lastStatus", evaluation.status().name()
                ))
        );

        syncExternalSettlement(collateral, proof, request, evaluation);
        return evaluation;
    }

    private int currentAttemptCount(String summaryJson) {
        JsonNode summaryNode = jsonService.readTree(summaryJson);
        return summaryNode.path("attemptCount").asInt(0);
    }

    private SettlementEvaluation evaluateProof(OfflinePaymentProof proof, CollateralLock collateral) {
        try {
            proofSchemaValidator.validate(proof);
        } catch (IllegalArgumentException exception) {
            return rejected("INVALID_PROOF_SCHEMA", proof, exception.getMessage());
        }

        Device device = deviceRepository.findByDeviceId(proof.senderDeviceId())
                .orElse(null);
        if (device == null) {
            return rejected("DEVICE_NOT_FOUND", proof, "sender device missing");
        }
        DeviceBindingVerificationService.VerificationResult bindingVerification = deviceBindingVerificationService.verify(device, proof);
        if (!bindingVerification.valid()) {
            return rejected("INVALID_DEVICE_BINDING", proof, bindingVerification.detail());
        }
        DeviceSignatureVerificationService.VerificationResult signatureVerification = deviceSignatureVerificationService.verify(device, proof);
        if (!signatureVerification.verified() && !signatureVerification.unsupported()) {
            return rejected("INVALID_DEVICE_SIGNATURE", proof, signatureVerification.detail());
        }
        if (settlementResultRepository.existsByVoucherId(proof.voucherId())) {
            return conflicted("DUPLICATE_SETTLEMENT", proof, "{\"reasonCode\":\"DUPLICATE_SETTLEMENT\"}");
        }

        List<OfflinePaymentProof> existingProofs = proofRepository.findByCollateralId(proof.collateralId()).stream()
                .filter(existing -> !existing.id().equals(proof.id()))
                .toList();

        ConflictDetectionResult conflictResult = proofConflictDetector.detect(existingProofs, proof);
        if (conflictResult.conflicted()) {
            return conflicted(conflictResult.conflictType(), proof, conflictResult.detailJson());
        }

        ChainValidationResult chainResult = proofChainValidator.validate(collateral, existingProofs, proof);
        if (!chainResult.valid()) {
            return rejected(chainResult.reasonCode(), proof, chainResult.detailJson());
        }

        return settlementPolicyEvaluator.evaluate(proof, collateral, device);
    }

    private SettlementEvaluation rejected(String reasonCode, OfflinePaymentProof proof, String detailJson) {
        return new SettlementEvaluation(
                SettlementStatus.REJECTED,
                false,
                reasonCode,
                jsonService.write(Map.of(
                        "voucherId", proof.voucherId(),
                        "reasonCode", reasonCode,
                        "detail", detailJson
                )),
                BigDecimal.ZERO,
                "ADJUST"
        );
    }

    private SettlementEvaluation conflicted(String reasonCode, OfflinePaymentProof proof, String detailJson) {
        return new SettlementEvaluation(
                SettlementStatus.CONFLICT,
                true,
                reasonCode,
                jsonService.write(Map.of(
                        "voucherId", proof.voucherId(),
                        "reasonCode", reasonCode,
                        "detail", detailJson
                )),
                BigDecimal.ZERO,
                "ADJUST"
        );
    }

    private void syncExternalSettlement(
            CollateralLock collateral,
            OfflinePaymentProof proof,
            SettlementRequest request,
            SettlementEvaluation evaluation
    ) {
        if (evaluation.conflictDetected()) {
            settlementConflictRepository.save(
                    request.id(),
                    proof.voucherId(),
                    collateral.id(),
                    proof.senderDeviceId(),
                    evaluation.reasonCode() == null ? "UNKNOWN_CONFLICT" : evaluation.reasonCode(),
                    "HIGH",
                    evaluation.resultJson()
            );
            SettlementStreamEventFactory.ConflictEvent conflictEvent = settlementStreamEventFactory
                    .conflictEvent(
                            request,
                            proof,
                            evaluation.reasonCode() == null ? "UNKNOWN_CONFLICT" : evaluation.reasonCode()
                    );
            eventBus.publishConflict(
                    conflictEvent.batchId(),
                    conflictEvent.voucherId(),
                    conflictEvent.collateralId(),
                    conflictEvent.conflictType(),
                    conflictEvent.severity(),
                    conflictEvent.createdAt()
            );
        }

        coinManageSettlementPort.finalizeSettlement(
                settlementSyncCommandFactory.createLedgerCommand(
                        collateral,
                        proof,
                        proof.amount(),
                        request,
                        evaluation.status().name(),
                        evaluation.releaseAction(),
                        evaluation.conflictDetected()
                )
        );
        foxCoinHistoryPort.recordSettlementHistory(
                settlementSyncCommandFactory.createHistoryCommand(
                        collateral,
                        proof.id(),
                        proof.amount(),
                        request,
                        evaluation.status().name(),
                        evaluation.conflictDetected()
                )
        );
    }

    private SettlementBatchStatus resolveBatchStatus(int settledCount, int failedCount, boolean hasConflict) {
        if (settledCount > 0 && failedCount > 0) {
            return SettlementBatchStatus.PARTIALLY_SETTLED;
        }
        if (settledCount > 0 && !hasConflict) {
            return SettlementBatchStatus.SETTLED;
        }
        return SettlementBatchStatus.FAILED;
    }

    private CollateralStatus resolveCollateralStatus(
            CollateralLock collateral,
            OfflinePaymentProof proof,
            SettlementEvaluation evaluation
    ) {
        if (evaluation.conflictDetected()) {
            return CollateralStatus.FROZEN;
        }
        if (evaluation.status() == SettlementStatus.EXPIRED) {
            return CollateralStatus.EXPIRED;
        }
        if (evaluation.status() != SettlementStatus.SETTLED) {
            return collateral.status();
        }
        return proof.amount().compareTo(collateral.remainingAmount()) >= 0
                ? CollateralStatus.RELEASED
                : CollateralStatus.PARTIALLY_SETTLED;
    }

    public record SubmitSettlementBatchCommand(
            UploaderType uploaderType,
            String uploaderDeviceId,
            String idempotencyKey,
            List<ProofSubmission> proofs
    ) {}

    public record ProofSubmission(
            String voucherId,
            String collateralId,
            String senderDeviceId,
            String receiverDeviceId,
            int keyVersion,
            int policyVersion,
            long counter,
            String nonce,
            String hashChainHead,
            String previousHash,
            String signature,
            BigDecimal amount,
            long timestampMs,
            long expiresAtMs,
            String canonicalPayload,
            Map<String, Object> payload
    ) {}

    public enum UploaderType {
        SENDER,
        RECEIVER
    }

    public record BatchFailureOutcome(
            String batchId,
            int attemptCount,
            boolean deadLettered
    ) {}
}
