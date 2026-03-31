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
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
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
import io.korion.offlinepay.application.service.settlement.ProofPayloadConsistencyValidator;
import io.korion.offlinepay.application.service.settlement.SettlementEvaluation;
import io.korion.offlinepay.application.service.settlement.SettlementPolicyEvaluator;
import io.korion.offlinepay.application.service.settlement.DeviceSignatureVerificationService;
import io.korion.offlinepay.application.service.settlement.DeviceBindingVerificationService;
import io.korion.offlinepay.application.service.settlement.IssuedProofVerificationService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
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
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final SettlementConflictRepository settlementConflictRepository;
    private final SettlementBatchEventBus eventBus;
    private final OfflineSagaService offlineSagaService;
    private final JsonService jsonService;
    private final SettlementBatchFactory settlementBatchFactory;
    private final SettlementRequestFactory settlementRequestFactory;
    private final SettlementStreamEventFactory settlementStreamEventFactory;
    private final SettlementSyncCommandFactory settlementSyncCommandFactory;
    private final ProofSchemaValidator proofSchemaValidator;
    private final ProofPayloadConsistencyValidator proofPayloadConsistencyValidator;
    private final ProofConflictDetector proofConflictDetector;
    private final ProofChainValidator proofChainValidator;
    private final SettlementPolicyEvaluator settlementPolicyEvaluator;
    private final DeviceSignatureVerificationService deviceSignatureVerificationService;
    private final DeviceBindingVerificationService deviceBindingVerificationService;
    private final IssuedProofVerificationService issuedProofVerificationService;

    public SettlementApplicationService(
            CollateralRepository collateralRepository,
            DeviceRepository deviceRepository,
            OfflinePaymentProofRepository proofRepository,
            SettlementBatchRepository batchRepository,
            SettlementRepository settlementRepository,
            SettlementResultRepository settlementResultRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            SettlementConflictRepository settlementConflictRepository,
            SettlementBatchEventBus eventBus,
            OfflineSagaService offlineSagaService,
            CoinManageSettlementPort coinManageSettlementPort,
            FoxCoinHistoryPort foxCoinHistoryPort,
            JsonService jsonService,
            SettlementBatchFactory settlementBatchFactory,
            SettlementRequestFactory settlementRequestFactory,
            SettlementStreamEventFactory settlementStreamEventFactory,
            SettlementSyncCommandFactory settlementSyncCommandFactory,
            ProofSchemaValidator proofSchemaValidator,
            ProofPayloadConsistencyValidator proofPayloadConsistencyValidator,
            ProofConflictDetector proofConflictDetector,
            ProofChainValidator proofChainValidator,
            SettlementPolicyEvaluator settlementPolicyEvaluator,
            DeviceSignatureVerificationService deviceSignatureVerificationService,
            DeviceBindingVerificationService deviceBindingVerificationService,
            IssuedProofVerificationService issuedProofVerificationService
    ) {
        this.collateralRepository = collateralRepository;
        this.deviceRepository = deviceRepository;
        this.proofRepository = proofRepository;
        this.batchRepository = batchRepository;
        this.settlementRepository = settlementRepository;
        this.settlementResultRepository = settlementResultRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.settlementConflictRepository = settlementConflictRepository;
        this.eventBus = eventBus;
        this.offlineSagaService = offlineSagaService;
        this.jsonService = jsonService;
        this.settlementBatchFactory = settlementBatchFactory;
        this.settlementRequestFactory = settlementRequestFactory;
        this.settlementStreamEventFactory = settlementStreamEventFactory;
        this.settlementSyncCommandFactory = settlementSyncCommandFactory;
        this.proofSchemaValidator = proofSchemaValidator;
        this.proofPayloadConsistencyValidator = proofPayloadConsistencyValidator;
        this.proofConflictDetector = proofConflictDetector;
        this.proofChainValidator = proofChainValidator;
        this.settlementPolicyEvaluator = settlementPolicyEvaluator;
        this.deviceSignatureVerificationService = deviceSignatureVerificationService;
        this.deviceBindingVerificationService = deviceBindingVerificationService;
        this.issuedProofVerificationService = issuedProofVerificationService;
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
                null,
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
                    extractChannelType(submission.payload()),
                    jsonService.write(submission.payload())
            );

            SettlementRequest request = settlementRepository.save(
                    batch.id(),
                    collateral.id(),
                    proof.id(),
                    SettlementStatus.PENDING,
                    null,
                    false,
                    settlementRequestFactory.uploadedResult()
            );
            requestIds.add(request.id());
            offlineSagaService.start(
                    OfflineSagaType.SETTLEMENT,
                    request.id(),
                    "SETTLEMENT_ACCEPTED",
                    Map.of(
                            "settlementId", request.id(),
                            "batchId", batch.id(),
                            "proofId", proof.id(),
                            "collateralId", collateral.id()
                    )
            );
        }

        batchRepository.updateStatus(
                batch.id(),
                SettlementBatchStatus.UPLOADED,
                null,
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
                null,
                settlementBatchFactory.validatingSummary()
        );
        for (SettlementRequest request : settlementRepository.findByBatchId(batchId)) {
            settlementRepository.update(
                    request.id(),
                    SettlementStatus.VALIDATING,
                    null,
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
        String batchReasonCode = failedCount > 0
                ? (settledCount > 0 ? OfflinePayReasonCode.PARTIAL_SETTLEMENT : OfflinePayReasonCode.SERVER_VALIDATION_FAIL)
                : null;
        String normalizedBatchReasonCode = normalizeBatchReasonCode(batchStatus, batchReasonCode);
        batchRepository.updateStatus(
                batch.id(),
                batchStatus,
                normalizedBatchReasonCode,
                jsonService.write(Map.of(
                        "acceptedCount", settledCount,
                        "failedCount", failedCount,
                        "hasConflict", hasConflict,
                        "finalizedAt", OffsetDateTime.now().toString()
                ))
        );
        saveBatchReconciliationCase(batch, batchStatus, normalizedBatchReasonCode, settledCount, failedCount, hasConflict);
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
                normalizeBatchReasonCode(deadLettered ? SettlementBatchStatus.FAILED : batch.status(), OfflinePayReasonCode.BATCH_SYNC_FAIL),
                deadLettered
                        ? settlementBatchFactory.deadLetterSummary(attemptCount, errorMessage, OfflinePayReasonCode.BATCH_SYNC_FAIL)
                        : settlementBatchFactory.failureSummary(attemptCount, errorMessage, OfflinePayReasonCode.BATCH_SYNC_FAIL)
        );
        if (deadLettered) {
            reconciliationCaseRepository.save(
                    null,
                    batch.id(),
                    null,
                    null,
                    "BATCH_SYNC_FAILED",
                    ReconciliationCaseStatus.OPEN,
                    OfflinePayReasonCode.BATCH_SYNC_FAIL,
                    jsonService.write(Map.of(
                            "batchId", batch.id(),
                            "attemptCount", attemptCount,
                            "errorMessage", errorMessage
                    ))
            );
        }
        return new BatchFailureOutcome(batch.id(), attemptCount, deadLettered);
    }

    private SettlementEvaluation processSettlementRequest(SettlementRequest request) {
        OfflinePaymentProof proof = proofRepository.findById(request.proofId())
                .orElseThrow(() -> new IllegalArgumentException("proof not found: " + request.proofId()));
        CollateralLock collateral = collateralRepository.findById(request.collateralId())
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + request.collateralId()));

        proofRepository.updateLifecycle(proof.id(), OfflineProofStatus.CONSUMED_PENDING_SETTLEMENT, null, true, false, false);
        SettlementEvaluation evaluation = evaluateProof(proof, collateral);
        String terminalReasonCode = normalizeSettlementReasonCode(evaluation.status(), evaluation.reasonCode(), evaluation.conflictDetected());
        proofRepository.updateLifecycle(
                proof.id(),
                mapProofStatus(evaluation.status(), evaluation.conflictDetected()),
                normalizeProofReasonCode(mapProofStatus(evaluation.status(), evaluation.conflictDetected()), terminalReasonCode),
                true,
                evaluation.status() == SettlementStatus.SETTLED,
                evaluation.status() == SettlementStatus.SETTLED
        );
        if (evaluation.status() == SettlementStatus.SETTLED) {
            collateralRepository.deductLockedAndRemainingAmount(collateral.id(), proof.amount());
            issuedProofVerificationService.markConsumed(proof);
        }

        settlementRepository.update(
                request.id(),
                evaluation.status(),
                terminalReasonCode,
                evaluation.conflictDetected(),
                evaluation.resultJson()
        );
        settlementResultRepository.save(
                request.id(),
                request.batchId(),
                proof,
                evaluation.status(),
                terminalReasonCode,
                evaluation.resultJson(),
                evaluation.settledAmount()
        );
        saveReconciliationCase(request, proof, evaluation);
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
            return rejected(OfflinePayReasonCode.INVALID_PROOF_SCHEMA, proof, exception.getMessage());
        }
        ProofPayloadConsistencyValidator.ValidationResult payloadValidation = proofPayloadConsistencyValidator.validate(proof);
        if (!payloadValidation.passed()) {
            return rejected(payloadValidation.reasonCode(), proof, payloadValidation.detailJson());
        }
        IssuedProofVerificationService.VerificationResult issuedProofVerification = issuedProofVerificationService.verify(proof);
        if (!issuedProofVerification.valid()) {
            return rejected(issuedProofVerification.reasonCode(), proof, issuedProofVerification.detail());
        }

        Device device = deviceRepository.findByDeviceId(proof.senderDeviceId())
                .orElse(null);
        if (device == null) {
            return rejected(OfflinePayReasonCode.DEVICE_NOT_FOUND, proof, "sender device missing");
        }
        DeviceBindingVerificationService.VerificationResult bindingVerification = deviceBindingVerificationService.verify(device, proof);
        if (!bindingVerification.valid()) {
            return rejected(OfflinePayReasonCode.INVALID_DEVICE_BINDING, proof, bindingVerification.detail());
        }
        String requestId = extractRequestId(proof);
        if (requestId != null && proofRepository.findBySenderRequestId(proof.senderDeviceId(), requestId)
                .filter(existing -> !existing.id().equals(proof.id()))
                .isPresent()) {
            return rejected(OfflinePayReasonCode.REQUEST_ID_REPLAYED, proof, "requestId replay detected");
        }
        DeviceSignatureVerificationService.VerificationResult signatureVerification = deviceSignatureVerificationService.verify(device, proof);
        if (!signatureVerification.verified() && !signatureVerification.unsupported()) {
            return rejected(OfflinePayReasonCode.INVALID_DEVICE_SIGNATURE, proof, signatureVerification.detail());
        }
        if (settlementResultRepository.existsByVoucherId(proof.voucherId())) {
            return conflicted(
                    OfflinePayReasonCode.DUPLICATE_SETTLEMENT,
                    proof,
                    "{\"reasonCode\":\"" + OfflinePayReasonCode.DUPLICATE_SETTLEMENT + "\"}"
            );
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

        proofRepository.updateLifecycle(
                proof.id(),
                OfflineProofStatus.VERIFIED_OFFLINE,
                null,
                true,
                true,
                false
        );

        return settlementPolicyEvaluator.evaluate(proof, collateral, device);
    }

    private String extractRequestId(OfflinePaymentProof proof) {
        String requestId = jsonService.readTree(proof.rawPayloadJson()).path("requestId").asText(null);
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requestId.trim();
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
                    evaluation.reasonCode() == null ? OfflinePayReasonCode.UNKNOWN_CONFLICT : evaluation.reasonCode(),
                    "HIGH",
                    evaluation.resultJson()
            );
            SettlementStreamEventFactory.ConflictEvent conflictEvent = settlementStreamEventFactory
                    .conflictEvent(
                            request,
                            proof,
                            evaluation.reasonCode() == null ? OfflinePayReasonCode.UNKNOWN_CONFLICT : evaluation.reasonCode()
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

        CoinManageSettlementPort.SettlementLedgerCommand ledgerCommand = settlementSyncCommandFactory.createLedgerCommand(
                collateral,
                proof,
                proof.amount(),
                request,
                evaluation.status().name(),
                evaluation.releaseAction(),
                evaluation.conflictDetected()
        );
        FoxCoinHistoryPort.SettlementHistoryCommand historyCommand = settlementSyncCommandFactory.createHistoryCommand(
                collateral,
                proof.id(),
                proof.amount(),
                request,
                evaluation.status().name(),
                evaluation.conflictDetected()
        );
        eventBus.publishExternalSyncRequested(
                "LEDGER_SYNC_REQUESTED",
                request.id(),
                request.batchId(),
                proof.id(),
                jsonService.write(Map.ofEntries(
                        Map.entry("settlementId", request.id()),
                        Map.entry("batchId", request.batchId()),
                        Map.entry("proofId", proof.id()),
                        Map.entry("voucherId", proof.voucherId()),
                        Map.entry("ledgerCommand", Map.ofEntries(
                                Map.entry("settlementId", ledgerCommand.settlementId()),
                                Map.entry("batchId", ledgerCommand.batchId()),
                                Map.entry("collateralId", ledgerCommand.collateralId()),
                                Map.entry("proofId", ledgerCommand.proofId()),
                                Map.entry("userId", ledgerCommand.userId()),
                                Map.entry("deviceId", ledgerCommand.deviceId()),
                                Map.entry("assetCode", ledgerCommand.assetCode()),
                                Map.entry("amount", ledgerCommand.amount()),
                                Map.entry("settlementStatus", ledgerCommand.settlementStatus()),
                                Map.entry("releaseAction", ledgerCommand.releaseAction()),
                                Map.entry("conflictDetected", ledgerCommand.conflictDetected()),
                                Map.entry("proofFingerprint", ledgerCommand.proofFingerprint()),
                                Map.entry("newStateHash", ledgerCommand.newStateHash()),
                                Map.entry("previousHash", ledgerCommand.previousHash()),
                                Map.entry("monotonicCounter", ledgerCommand.monotonicCounter()),
                                Map.entry("nonce", ledgerCommand.nonce()),
                                Map.entry("signature", ledgerCommand.signature())
                        )),
                        Map.entry("historyCommand", Map.ofEntries(
                                Map.entry("settlementId", historyCommand.settlementId()),
                                Map.entry("batchId", historyCommand.batchId()),
                                Map.entry("collateralId", historyCommand.collateralId()),
                                Map.entry("proofId", historyCommand.proofId()),
                                Map.entry("userId", historyCommand.userId()),
                                Map.entry("deviceId", historyCommand.deviceId()),
                                Map.entry("assetCode", historyCommand.assetCode()),
                                Map.entry("amount", historyCommand.amount()),
                                Map.entry("settlementStatus", historyCommand.settlementStatus()),
                                Map.entry("historyType", historyCommand.historyType())
                        )),
                        Map.entry("requestedAt", OffsetDateTime.now().toString())
                )),
                OffsetDateTime.now().toString()
        );
    }

    private void saveReconciliationCase(SettlementRequest request, OfflinePaymentProof proof, SettlementEvaluation evaluation) {
        if (evaluation.status() == SettlementStatus.SETTLED && !evaluation.conflictDetected()) {
            return;
        }
        String reasonCode = requireReasonCode(
                evaluation.reasonCode() == null || evaluation.reasonCode().isBlank()
                        ? OfflinePayReasonCode.SETTLEMENT_FAIL
                        : evaluation.reasonCode(),
                "reconciliation case"
        );
        String caseType = resolveReconciliationCaseType(evaluation, reasonCode);
        reconciliationCaseRepository.save(
                request.id(),
                request.batchId(),
                proof.id(),
                proof.voucherId(),
                caseType,
                ReconciliationCaseStatus.OPEN,
                reasonCode,
                evaluation.resultJson()
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

    private String extractChannelType(Map<String, Object> payload) {
        if (payload == null) {
            return "UNKNOWN";
        }
        Object channelType = payload.get("channelType");
        if (channelType instanceof String channel && !channel.isBlank()) {
            return channel.trim().toUpperCase();
        }
        Object connectionType = payload.get("connectionType");
        if (connectionType instanceof String connection && !connection.isBlank()) {
            return connection.trim().toUpperCase();
        }
        return "UNKNOWN";
    }

    private OfflineProofStatus mapProofStatus(SettlementStatus status, boolean conflictDetected) {
        if (conflictDetected || status == SettlementStatus.CONFLICT) {
            return OfflineProofStatus.CONFLICTED;
        }
        return switch (status) {
            case SETTLED -> OfflineProofStatus.SETTLED;
            case EXPIRED -> OfflineProofStatus.EXPIRED;
            case REJECTED -> OfflineProofStatus.REJECTED;
            case VALIDATING, PENDING -> OfflineProofStatus.CONSUMED_PENDING_SETTLEMENT;
            default -> OfflineProofStatus.FAILED;
        };
    }

    private String normalizeSettlementReasonCode(SettlementStatus status, String reasonCode, boolean conflictDetected) {
        if (conflictDetected || status == SettlementStatus.CONFLICT) {
            return requireReasonCode(reasonCode, "settlement conflict");
        }
        return switch (status) {
            case SETTLED -> OfflinePayReasonCode.SETTLED;
            case REJECTED, EXPIRED -> requireReasonCode(reasonCode, "settlement failure");
            case VALIDATING, PENDING -> reasonCode;
            default -> requireReasonCode(reasonCode, "settlement terminal status");
        };
    }

    private String normalizeBatchReasonCode(SettlementBatchStatus status, String reasonCode) {
        return switch (status) {
            case FAILED, PARTIALLY_SETTLED, CLOSED -> requireReasonCode(reasonCode, "batch terminal status");
            case SETTLED -> OfflinePayReasonCode.SETTLED;
            case CREATED, UPLOADED, VALIDATING -> reasonCode;
        };
    }

    private String normalizeProofReasonCode(OfflineProofStatus status, String reasonCode) {
        return switch (status) {
            case SETTLED -> OfflinePayReasonCode.SETTLED;
            case REJECTED, CONFLICTED, EXPIRED, FAILED -> requireReasonCode(reasonCode, "proof lifecycle failure");
            case ISSUED, UPLOADED, VALIDATING, VERIFIED_OFFLINE, CONSUMED_PENDING_SETTLEMENT -> reasonCode;
        };
    }

    private String requireReasonCode(String reasonCode, String context) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalStateException("reasonCode is required for " + context);
        }
        return reasonCode;
    }

    private String resolveReconciliationCaseType(SettlementEvaluation evaluation, String reasonCode) {
        if (OfflinePayReasonCode.DUPLICATE_SETTLEMENT.equals(reasonCode)) {
            return "DUPLICATE_SETTLEMENT";
        }
        if (OfflinePayReasonCode.DUPLICATE_NONCE.equals(reasonCode)
                || OfflinePayReasonCode.DUPLICATE_COUNTER.equals(reasonCode)) {
            return "DUPLICATE_SEND";
        }
        if (reasonCode != null && reasonCode.startsWith("ISSUED_PROOF_")) {
            return "ISSUED_PROOF_INVALID";
        }
        if (reasonCode != null && reasonCode.startsWith("PAYLOAD_")) {
            return "PAYLOAD_INVALID";
        }
        if (OfflinePayReasonCode.DEVICE_NOT_FOUND.equals(reasonCode)
                || OfflinePayReasonCode.INVALID_DEVICE_BINDING.equals(reasonCode)
                || OfflinePayReasonCode.INVALID_DEVICE_SIGNATURE.equals(reasonCode)
                || OfflinePayReasonCode.DEVICE_NOT_ACTIVE.equals(reasonCode)
                || OfflinePayReasonCode.KEY_VERSION_MISMATCH.equals(reasonCode)) {
            return "DEVICE_INVALID";
        }
        if (OfflinePayReasonCode.INVALID_STATE_HASH.equals(reasonCode)
                || OfflinePayReasonCode.INVALID_GENESIS_COUNTER.equals(reasonCode)
                || OfflinePayReasonCode.INVALID_GENESIS_LINK.equals(reasonCode)
                || OfflinePayReasonCode.COUNTER_GAP.equals(reasonCode)
                || OfflinePayReasonCode.INVALID_PREVIOUS_HASH.equals(reasonCode)
                || OfflinePayReasonCode.PROOF_EXPIRED.equals(reasonCode)
                || OfflinePayReasonCode.COLLATERAL_EXPIRED.equals(reasonCode)) {
            return "CHAIN_INVALID";
        }
        if (evaluation.conflictDetected()) {
            return "CONFLICT";
        }
        return "FAILED_SETTLEMENT";
    }

    private void saveBatchReconciliationCase(
            SettlementBatch batch,
            SettlementBatchStatus batchStatus,
            String reasonCode,
            int settledCount,
            int failedCount,
            boolean hasConflict
    ) {
        if (batchStatus != SettlementBatchStatus.PARTIALLY_SETTLED) {
            return;
        }
        reconciliationCaseRepository.save(
                null,
                batch.id(),
                null,
                null,
                "PARTIAL_SETTLEMENT",
                ReconciliationCaseStatus.OPEN,
                requireReasonCode(reasonCode, "batch reconciliation"),
                jsonService.write(Map.of(
                        "batchId", batch.id(),
                        "settledCount", settledCount,
                        "failedCount", failedCount,
                        "hasConflict", hasConflict,
                        "retryable", true,
                        "nextAction", "REVIEW_AND_RETRY_FAILED_SETTLEMENTS"
                ))
        );
    }

    private Map<String, Object> mergeDetail(Map<String, Object> base, Map<String, Object> extra) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>(base);
        merged.putAll(extra);
        return merged;
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
