package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.korion.offlinepay.application.factory.SettlementBatchFactory;
import io.korion.offlinepay.application.factory.SettlementRequestFactory;
import io.korion.offlinepay.application.factory.SettlementStreamEventFactory;
import io.korion.offlinepay.application.factory.SettlementSyncCommandFactory;
import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.port.OfflinePayLocalEvidenceRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.application.port.OfflineSagaRepository;
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
import io.korion.offlinepay.application.service.settlement.OfflinePaySettlementFeeCalculator;
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
import io.korion.offlinepay.domain.model.OfflinePayLocalEvidence;
import io.korion.offlinepay.domain.model.OfflineSaga;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.policy.DeviceTrustContract;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementApplicationService {
    private static final Set<String> ALLOWED_TRANSPORT_TRANSCRIPT_SOURCES = Set.of(
            "NATIVE_BLE_SEND_TRANSCRIPT_V1",
            "NATIVE_BLE_RECEIVE_TRANSCRIPT_V1",
            "NATIVE_NFC_BRIDGE_TRANSCRIPT_V1",
            "NATIVE_QR_SCAN_TRANSCRIPT_V1"
    );
    private static final Set<String> SENDER_AUTH_COMPLETED_SAGA_STATUSES = Set.of(
            "SENDER_PROOF_COMMITTED",
            "COMPLETE_PREPARE_SENT",
            "COMPLETE_SUMMARY_SENDING",
            "COMPLETE_SUMMARY_ACK_WAITING",
            "COMPLETE_FINALIZE_SENDING",
            "COMPLETE_FINALIZE_ACK_WAITING",
            "COMPLETE_SENT",
            "COMPLETE_ACKED",
            "COMPLETED"
    );

    private final CollateralRepository collateralRepository;
    private final CollateralOperationRepository collateralOperationRepository;
    private final DeviceRepository deviceRepository;
    private final OfflinePayLocalEvidenceRepository localEvidenceRepository;
    private final OfflinePaymentProofRepository proofRepository;
    private final SettlementBatchRepository batchRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementResultRepository settlementResultRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final OfflineSagaRepository offlineSagaRepository;
    private final SettlementConflictRepository settlementConflictRepository;
    private final SettlementBatchEventBus eventBus;
    private final OfflineSagaService offlineSagaService;
    private final CoinManageSettlementPort coinManageSettlementPort;
    private final JsonService jsonService;
    private final SettlementBatchFactory settlementBatchFactory;
    private final SettlementRequestFactory settlementRequestFactory;
    private final SettlementStreamEventFactory settlementStreamEventFactory;
    private final SettlementSyncCommandFactory settlementSyncCommandFactory;
    private final OfflinePaySettlementFeeCalculator feeCalculator;
    private final ProofSchemaValidator proofSchemaValidator;
    private final ProofPayloadConsistencyValidator proofPayloadConsistencyValidator;
    private final ProofConflictDetector proofConflictDetector;
    private final ProofChainValidator proofChainValidator;
    private final SettlementPolicyEvaluator settlementPolicyEvaluator;
    private final DeviceSignatureVerificationService deviceSignatureVerificationService;
    private final DeviceBindingVerificationService deviceBindingVerificationService;
    private final IssuedProofVerificationService issuedProofVerificationService;
    private final OfflinePayDeviceIdentifierResolver deviceIdentifierResolver;

    public SettlementApplicationService(
            CollateralRepository collateralRepository,
            CollateralOperationRepository collateralOperationRepository,
            DeviceRepository deviceRepository,
            OfflinePayLocalEvidenceRepository localEvidenceRepository,
            OfflinePaymentProofRepository proofRepository,
            SettlementBatchRepository batchRepository,
            SettlementRepository settlementRepository,
            SettlementResultRepository settlementResultRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            OfflineSagaRepository offlineSagaRepository,
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
            OfflinePaySettlementFeeCalculator feeCalculator,
            ProofSchemaValidator proofSchemaValidator,
            ProofPayloadConsistencyValidator proofPayloadConsistencyValidator,
            ProofConflictDetector proofConflictDetector,
            ProofChainValidator proofChainValidator,
            SettlementPolicyEvaluator settlementPolicyEvaluator,
            DeviceSignatureVerificationService deviceSignatureVerificationService,
            DeviceBindingVerificationService deviceBindingVerificationService,
            IssuedProofVerificationService issuedProofVerificationService,
            OfflinePayDeviceIdentifierResolver deviceIdentifierResolver
    ) {
        this.collateralRepository = collateralRepository;
        this.collateralOperationRepository = collateralOperationRepository;
        this.deviceRepository = deviceRepository;
        this.localEvidenceRepository = localEvidenceRepository;
        this.proofRepository = proofRepository;
        this.batchRepository = batchRepository;
        this.settlementRepository = settlementRepository;
        this.settlementResultRepository = settlementResultRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.offlineSagaRepository = offlineSagaRepository;
        this.settlementConflictRepository = settlementConflictRepository;
        this.eventBus = eventBus;
        this.offlineSagaService = offlineSagaService;
        this.coinManageSettlementPort = coinManageSettlementPort;
        this.jsonService = jsonService;
        this.settlementBatchFactory = settlementBatchFactory;
        this.settlementRequestFactory = settlementRequestFactory;
        this.settlementStreamEventFactory = settlementStreamEventFactory;
        this.settlementSyncCommandFactory = settlementSyncCommandFactory;
        this.feeCalculator = feeCalculator;
        this.proofSchemaValidator = proofSchemaValidator;
        this.proofPayloadConsistencyValidator = proofPayloadConsistencyValidator;
        this.proofConflictDetector = proofConflictDetector;
        this.proofChainValidator = proofChainValidator;
        this.settlementPolicyEvaluator = settlementPolicyEvaluator;
        this.deviceSignatureVerificationService = deviceSignatureVerificationService;
        this.deviceBindingVerificationService = deviceBindingVerificationService;
        this.issuedProofVerificationService = issuedProofVerificationService;
        this.deviceIdentifierResolver = deviceIdentifierResolver;
    }

    @Transactional
    public SettlementBatch submitBatch(SubmitSettlementBatchCommand command) {
        return batchRepository.findByIdempotencyKey(command.idempotencyKey())
                .orElseGet(() -> createBatch(command));
    }

    @Transactional
    public ReceiverSettlementConfirmationResult confirmReceivedSettlements(ConfirmReceivedSettlementsCommand command) {
        int requested = command.proofIds() == null ? 0 : command.proofIds().size();
        int confirmed = 0;
        int skipped = 0;
        for (String proofId : command.proofIds() == null ? List.<String>of() : command.proofIds()) {
            if (proofId == null || proofId.isBlank()) {
                skipped++;
                continue;
            }
            OfflinePaymentProof proof = proofRepository.findById(proofId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("offline proof not found: " + proofId));
            if (!proofBelongsToReceiverUser(proof, command.userId())) {
                throw new IllegalArgumentException("received proof does not belong to user: " + proof.id());
            }
            if (proof.receivedUnsettledAmount() == null || proof.receivedUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                continue;
            }
            SettlementRequest request = settlementRepository.findLatestByProofId(proof.id())
                    .orElseThrow(() -> new IllegalStateException("settlement request not found for proof: " + proof.id()));
            if (!isReceiverSettlementConfirmable(proof, request)) {
                skipped++;
                continue;
            }
            handleReceiverOnlineConfirmation(proof, request);
            confirmed++;
        }
        return new ReceiverSettlementConfirmationResult(requested, confirmed, skipped);
    }

    @Transactional
    public LocalEvidenceIngestResult ingestLocalEvidence(LocalEvidenceIngestCommand command) {
        int requested = command.evidences() == null ? 0 : command.evidences().size();
        int stored = 0;
        int skipped = 0;
        int matched = 0;
        int awaitingCarrier = 0;
        for (LocalEvidenceSubmission evidence : command.evidences() == null ? List.<LocalEvidenceSubmission>of() : command.evidences()) {
            if (evidence == null || !hasRequiredLocalEvidence(command, evidence)) {
                skipped++;
                continue;
            }
            String direction = evidence.direction().trim().toUpperCase();
            Map<String, Object> rawPayload = buildLocalEvidencePayload(evidence, direction);
            LocalEvidenceVerification verification = verifyDirectLocalEvidence(evidence, direction);
            localEvidenceRepository.save(new OfflinePayLocalEvidence(
                    null,
                    evidence.voucherId().trim(),
                    evidence.sessionId() == null ? null : evidence.sessionId().trim(),
                    direction,
                    command.uploaderType().name(),
                    command.uploaderDeviceId().trim(),
                    evidence.senderDeviceId().trim(),
                    evidence.receiverDeviceId().trim(),
                    evidence.amount(),
                    evidence.counter(),
                    evidence.previousHash() == null ? "" : evidence.previousHash().trim(),
                    evidence.hashChainHead().trim(),
                    evidence.nonce().trim(),
                    evidence.signature().trim(),
                    evidence.canonicalPayload().trim(),
                    rawPayload,
                    verification.status(),
                    verification.detail(),
                    null
            ));
            if (!"VERIFIED".equals(verification.status())) {
                skipped++;
                continue;
            }
            stored++;
            if (processMatchingSenderSettlement(evidence.voucherId().trim())) {
                matched++;
            } else {
                awaitingCarrier++;
            }
        }
        return new LocalEvidenceIngestResult(requested, stored, skipped, matched, awaitingCarrier);
    }

    @Transactional
    public DirectLocalEvidenceReconcileResult reconcileDirectLocalEvidence(int limit) {
        List<OfflinePayLocalEvidence> candidates =
                localEvidenceRepository.findVerifiedSenderEvidenceAwaitingCarrier(Math.max(1, limit));
        int created = 0;
        int reused = 0;
        int finalized = 0;
        int rejected = 0;
        int skipped = 0;
        List<String> batchIds = new ArrayList<>();
        List<String> settlementIds = new ArrayList<>();
        for (OfflinePayLocalEvidence evidence : candidates) {
            Optional<OfflinePaymentProof> existingProof = proofRepository.findByVoucherId(evidence.voucherId());
            if (existingProof.isPresent()) {
                Optional<SettlementRequest> request = settlementRepository.findLatestByProofId(existingProof.get().id());
                if (request.isEmpty()) {
                    skipped++;
                    continue;
                }
                SettlementRequest existingRequest = request.get();
                boolean retryFinancialHonor = shouldRetryFinancialHonorAfterLateReceiverEvidence(existingProof.get(), existingRequest);
                if (existingRequest.status() != SettlementStatus.PENDING
                        && existingRequest.status() != SettlementStatus.VALIDATING
                        && !retryFinancialHonor) {
                    skipped++;
                    continue;
                }
                SettlementEvaluation evaluation = processSettlementRequest(existingRequest, true, retryFinancialHonor);
                settlementIds.add(request.get().id());
                reused++;
                if (evaluation.status() == SettlementStatus.SETTLED) {
                    finalized++;
                } else if (evaluation.status() == SettlementStatus.REJECTED
                        || evaluation.status() == SettlementStatus.EXPIRED
                        || evaluation.status() == SettlementStatus.CONFLICT) {
                    rejected++;
                }
                continue;
            }
            Optional<ProofSubmission> proofSubmission = toDirectEvidenceProofSubmission(evidence);
            if (proofSubmission.isEmpty()) {
                skipped++;
                continue;
            }
            String idempotencyKey = "direct-local-evidence:" + evidence.voucherId();
            SettlementBatch batch = submitBatch(new SubmitSettlementBatchCommand(
                    UploaderType.SENDER,
                    evidence.senderDeviceId(),
                    idempotencyKey,
                    List.of(proofSubmission.get()),
                    "DIRECT_LOCAL_EVIDENCE"
            ));
            batchIds.add(batch.id());
            created++;
            Optional<OfflinePaymentProof> proof = proofRepository.findByVoucherId(evidence.voucherId());
            if (proof.isEmpty()) {
                skipped++;
                continue;
            }
            Optional<SettlementRequest> request = settlementRepository.findLatestByProofId(proof.get().id());
            if (request.isEmpty()) {
                skipped++;
                continue;
            }
            SettlementEvaluation evaluation = processSettlementRequest(request.get(), true);
            settlementIds.add(request.get().id());
            if (evaluation.status() == SettlementStatus.SETTLED) {
                finalized++;
            } else if (evaluation.status() == SettlementStatus.REJECTED
                    || evaluation.status() == SettlementStatus.EXPIRED
                    || evaluation.status() == SettlementStatus.CONFLICT) {
                rejected++;
            }
        }
        return new DirectLocalEvidenceReconcileResult(candidates.size(), created, reused, finalized, rejected, skipped, batchIds, settlementIds);
    }

    @Transactional
    public AutoConfirmPendingReceiverHistoryResult autoConfirmStalePendingReceiverHistory(
            List<OfflineSaga> staleSagas
    ) {
        int attempted = 0;
        int confirmed = 0;
        int skipped = 0;
        for (OfflineSaga saga : staleSagas) {
            String settlementId = saga.referenceId();
            SettlementRequest request = settlementRepository.findById(settlementId).orElse(null);
            if (request == null) {
                skipped++;
                continue;
            }
            OfflinePaymentProof proof = proofRepository.findById(request.proofId()).orElse(null);
            if (proof == null || proof.receiverDeviceId() == null || proof.receiverDeviceId().isBlank()) {
                skipped++;
                continue;
            }
            if (!isReceiverSettlementConfirmable(proof, request)) {
                skipped++;
                continue;
            }
            if (!shouldAutoConfirmReceiverSettlement(proof)) {
                skipped++;
                continue;
            }
            attempted++;
            handleReceiverOnlineConfirmation(proof, request);
            confirmed++;
        }
        return new AutoConfirmPendingReceiverHistoryResult(staleSagas.size(), attempted, confirmed, skipped);
    }

    public record AutoConfirmPendingReceiverHistoryResult(
            int candidates,
            int attempted,
            int confirmed,
            int skipped
    ) {}

    @Transactional(readOnly = true)
    public LocalEvidenceStatusResult getLocalEvidenceStatus(String voucherId, String sessionId) {
        return getLocalEvidenceStatus(voucherId, sessionId, 24);
    }

    @Transactional(readOnly = true)
    public LocalEvidenceStatusResult getLocalEvidenceStatus(String voucherId, String sessionId, int staleAfterHours) {
        String normalizedVoucherId = voucherId == null ? "" : voucherId.trim();
        String normalizedSessionId = sessionId == null ? "" : sessionId.trim();
        int normalizedStaleAfterHours = Math.max(1, staleAfterHours);
        var status = localEvidenceRepository.summarizeStatus(
                normalizedVoucherId.isBlank() ? null : normalizedVoucherId,
                normalizedSessionId.isBlank() ? null : normalizedSessionId,
                OffsetDateTime.now().minusHours(normalizedStaleAfterHours)
        );
        return new LocalEvidenceStatusResult(
                status.voucherId(),
                status.sessionId(),
                status.total(),
                status.stored(),
                status.matched(),
                status.awaitingCarrier(),
                status.failed(),
                status.senderStored(),
                status.receiverStored(),
                status.senderMatched(),
                status.receiverMatched(),
                status.senderFailed(),
                status.receiverFailed(),
                resolveLocalEvidenceState(status),
                status.staleAwaitingCarrier(),
                normalizedStaleAfterHours,
                status.oldestAwaitingCarrierAt(),
                status.latestUpdatedAt()
        );
    }

    private String resolveLocalEvidenceState(OfflinePayLocalEvidenceRepository.LocalEvidenceStatus status) {
        if (status.total() <= 0) {
            return "NOT_FOUND";
        }
        if (status.awaitingCarrier() > 0) {
            return "AWAITING_CARRIER";
        }
        if (status.matched() > 0 && status.failed() == 0) {
            return "MATCHED";
        }
        if (status.failed() > 0 && status.stored() == 0) {
            return "FAILED";
        }
        return "PARTIAL";
    }

    private Optional<ProofSubmission> toDirectEvidenceProofSubmission(OfflinePayLocalEvidence evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (evidence.rawPayload() != null) {
            payload.putAll(evidence.rawPayload());
        }
        boolean receiverEvidence = "RECEIVE".equalsIgnoreCase(evidence.direction());
        String collateralId = firstText(payload,
                receiverEvidence ? "senderProofCollateralId" : "collateralId",
                receiverEvidence ? "receiverEvidenceBlockSenderProofCollateralId" : "senderLocalBlockCollateralId",
                "collateralId",
                "senderLocalBlockCollateralId",
                "localBlockCollateralId");
        Integer keyVersion = firstPositiveInteger(payload,
                receiverEvidence ? "senderProofKeyVersion" : "keyVersion",
                receiverEvidence ? "receiverEvidenceBlockSenderProofKeyVersion" : "senderLocalBlockKeyVersion",
                "keyVersion",
                "senderLocalBlockKeyVersion",
                "localBlockKeyVersion");
        Integer policyVersion = firstPositiveInteger(payload,
                receiverEvidence ? "senderProofPolicyVersion" : "policyVersion",
                receiverEvidence ? "receiverEvidenceBlockSenderProofPolicyVersion" : "senderLocalBlockPolicyVersion",
                "policyVersion",
                "senderLocalBlockPolicyVersion",
                "localBlockPolicyVersion");
        Long timestampMs = firstPositiveLong(payload,
                receiverEvidence ? "senderProofTimestampMs" : "timestampMs",
                receiverEvidence ? "receiverEvidenceBlockSenderProofTimestampMs" : "senderLocalBlockTimestampMs",
                "timestampMs",
                "senderLocalBlockTimestampMs",
                "localBlockTimestampMs",
                "timestamp");
        Long expiresAtMs = firstEpochLong(payload,
                receiverEvidence ? "senderProofExpiresAtMs" : "expiresAtMs",
                receiverEvidence ? "receiverEvidenceBlockSenderProofExpiresAtMs" : "senderLocalBlockExpiresAtMs",
                "expiresAtMs",
                "senderLocalBlockExpiresAtMs",
                "localBlockExpiresAtMs",
                "expiresAt");
        Long counter = receiverEvidence
                ? firstPositiveLong(payload, "senderProofCounter", "receiverEvidenceBlockSenderProofCounter")
                : evidence.counter();
        String previousHash = receiverEvidence
                ? firstText(payload, "senderProofPrevHash", "receiverEvidenceBlockSenderProofPrevHash")
                : evidence.previousHash();
        String hashChainHead = receiverEvidence
                ? firstText(payload, "senderProofNewHash", "receiverEvidenceBlockSenderProofNewHash")
                : evidence.hashChainHead();
        String nonce = receiverEvidence
                ? firstText(payload, "senderProofNonce", "receiverEvidenceBlockSenderProofNonce")
                : evidence.nonce();
        String signature = receiverEvidence
                ? firstText(payload, "senderProofSignature", "receiverEvidenceBlockSenderProofSignature")
                : evidence.signature();
        String canonicalPayload = receiverEvidence
                ? firstText(payload, "senderProofCanonicalPayload", "receiverEvidenceBlockSenderProofCanonicalPayload")
                : evidence.canonicalPayload();
        if (collateralId.isBlank()
                || keyVersion == null
                || policyVersion == null
                || timestampMs == null
                || expiresAtMs == null
                || counter == null
                || evidence.amount() == null
                || hashChainHead == null
                || hashChainHead.isBlank()
                || signature == null
                || signature.isBlank()
                || nonce == null
                || nonce.isBlank()
                || canonicalPayload == null
                || canonicalPayload.isBlank()) {
            return Optional.empty();
        }

        payload.put("directLocalEvidenceCarrier", true);
        payload.put("senderLocalBlock", true);
        payload.putIfAbsent("senderLocalBlockVoucherId", evidence.voucherId());
        payload.putIfAbsent("senderLocalBlockCollateralId", collateralId);
        payload.putIfAbsent("senderLocalBlockAmount", evidence.amount().toPlainString());
        payload.putIfAbsent("senderLocalBlockSenderDeviceId", evidence.senderDeviceId());
        payload.putIfAbsent("senderLocalBlockReceiverDeviceId", evidence.receiverDeviceId());
        payload.putIfAbsent("senderLocalBlockCounter", counter);
        payload.putIfAbsent("senderLocalBlockPrevHash", previousHash);
        payload.putIfAbsent("senderLocalBlockNewHash", hashChainHead);
        payload.putIfAbsent("senderLocalBlockNonce", nonce);
        payload.putIfAbsent("senderLocalBlockSignature", signature);
        payload.putIfAbsent("senderLocalBlockCanonicalPayload", canonicalPayload);
        payload.putIfAbsent("senderLocalBlockTimestampMs", timestampMs);
        payload.putIfAbsent("senderLocalBlockExpiresAtMs", expiresAtMs);

        return Optional.of(new ProofSubmission(
                evidence.voucherId(),
                collateralId,
                evidence.senderDeviceId(),
                evidence.receiverDeviceId(),
                keyVersion,
                policyVersion,
                counter,
                nonce,
                hashChainHead,
                previousHash,
                signature,
                evidence.amount(),
                timestampMs,
                expiresAtMs,
                canonicalPayload,
                payload
        ));
    }

    private LocalEvidenceVerification verifyDirectLocalEvidence(LocalEvidenceSubmission evidence, String direction) {
        String canonicalPayload = evidence.canonicalPayload().trim();
        String submittedHash = evidence.hashChainHead().trim();
        if (!equalsText(sha256Hex(canonicalPayload), submittedHash)) {
            return new LocalEvidenceVerification("FAILED", "local evidence hash mismatch");
        }

        JsonNode canonical = jsonService.readTree(canonicalPayload);
        if (!equalsText(evidence.voucherId(), canonical.path("voucherId").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence voucher mismatch");
        }
        if (!equalsText(direction, canonical.path("direction").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence direction mismatch");
        }
        String expectedDeviceId = "SEND".equals(direction) ? evidence.senderDeviceId() : evidence.receiverDeviceId();
        if (!equalsText(expectedDeviceId, canonical.path("deviceId").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence device mismatch");
        }
        if (!equalsText(evidence.senderDeviceId(), canonical.path("senderDeviceId").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence sender device mismatch");
        }
        if (!equalsText(evidence.receiverDeviceId(), canonical.path("receiverDeviceId").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence receiver device mismatch");
        }
        if (!equalsText(evidence.previousHash(), canonical.path("prevHash").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence previous hash mismatch");
        }
        if (!equalsText(evidence.nonce(), canonical.path("nonce").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence nonce mismatch");
        }
        if (!equalsText(evidence.transportSessionHash(), canonical.path("transportSessionHash").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence transport session hash mismatch");
        }
        if (!equalsText(evidence.transportTranscriptSource(), canonical.path("transportTranscriptSource").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence transport transcript source mismatch");
        }
        String transportTranscriptError = verifyTransportTranscript(evidence, canonical);
        if (transportTranscriptError != null) {
            return new LocalEvidenceVerification("FAILED", transportTranscriptError);
        }
        if (!equalsText(evidence.deviceAttestationId(), canonical.path("deviceAttestationId").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence device attestation id mismatch");
        }
        if (!equalsText(evidence.deviceAttestationVerdict(), canonical.path("deviceAttestationVerdict").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence attestation verdict mismatch");
        }
        if (!equalsText(evidence.serverVerifiedTrustLevel(), canonical.path("serverVerifiedTrustLevel").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence trust level mismatch");
        }
        if (!equalsText(evidence.serverAttestationVerifiedAt(), canonical.path("serverAttestationVerifiedAt").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence attestation verified time mismatch");
        }
        if (canonical.path("counter").asLong(-1) != evidence.counter()) {
            return new LocalEvidenceVerification("FAILED", "local evidence counter mismatch");
        }
        if (!amountEquals(evidence.amount(), canonical.path("amount").asText(""))) {
            return new LocalEvidenceVerification("FAILED", "local evidence amount mismatch");
        }

        Device device = deviceIdentifierResolver.resolve(expectedDeviceId)
                .orElse(null);
        if (device == null) {
            return new LocalEvidenceVerification("FAILED", "local evidence device not found");
        }
        String securityBindingError = verifyLocalEvidenceSecurityBinding(evidence, device);
        if (securityBindingError != null) {
            return new LocalEvidenceVerification("FAILED", securityBindingError);
        }
        String sigAlgorithm = canonical.path("signatureAlgorithm").asText("");
        DeviceSignatureVerificationService.VerificationResult signatureVerification;
        if ("MANIFEST_V1".equals(sigAlgorithm)) {
            // submittedHash == sha256Hex(canonicalPayload) was verified above — reuse it.
            signatureVerification = deviceSignatureVerificationService.verifyManifestV1(
                    device, submittedHash,
                    canonical.path("amount").asText(""),
                    evidence.counter(),
                    expectedDeviceId, evidence.sessionId(),
                    canonical.path("nonce").asText(""),
                    canonical.path("createdAt").asText(""), evidence.signature());
        } else {
            signatureVerification = deviceSignatureVerificationService.verifyPayload(device, canonicalPayload, evidence.signature());
        }
        if (!signatureVerification.verified()) {
            return new LocalEvidenceVerification("FAILED", "local evidence signature invalid: " + signatureVerification.detail());
        }
        return new LocalEvidenceVerification("VERIFIED", "direct local evidence verified");
    }

    private String verifyLocalEvidenceSecurityBinding(LocalEvidenceSubmission evidence, Device device) {
        String expectedKeyId = "device:" + device.deviceId() + ":v" + device.keyVersion();
        if (evidence.keyId() == null || evidence.keyId().isBlank()) {
            return "local evidence key id missing";
        }
        if (!equalsText(expectedKeyId, evidence.keyId())) {
            return "local evidence key id mismatch";
        }
        if (evidence.publicKeyFingerprint() == null || evidence.publicKeyFingerprint().isBlank()) {
            return "local evidence public key fingerprint missing";
        }
        String expectedFingerprint = sha256Hex(normalizePublicKeyMaterial(device.publicKey()));
        if (!equalsText(expectedFingerprint, evidence.publicKeyFingerprint())) {
            return "local evidence public key fingerprint mismatch";
        }
        if (evidence.transportSessionHash() == null || evidence.transportSessionHash().isBlank()) {
            return "local evidence transport session hash missing";
        }
        if (!evidence.transportSessionHash().matches("(?i)^[0-9a-f]{64}$")) {
            return "local evidence transport session hash invalid";
        }
        if (evidence.transportTranscriptSource() == null || evidence.transportTranscriptSource().isBlank()) {
            return "local evidence transport transcript source missing";
        }
        if (!ALLOWED_TRANSPORT_TRANSCRIPT_SOURCES.contains(evidence.transportTranscriptSource().trim().toUpperCase())) {
            return "local evidence transport transcript source invalid";
        }
        if (evidence.deviceAttestationId() == null || evidence.deviceAttestationId().isBlank()) {
            return "local evidence device attestation id missing";
        }
        if (!DeviceTrustContract.MINIMUM_ATTESTATION_VERDICT.equals(evidence.deviceAttestationVerdict())) {
            return "local evidence device attestation verdict insufficient";
        }
        if (!DeviceTrustContract.SERVER_VERIFIED.equals(evidence.serverVerifiedTrustLevel())) {
            return "local evidence device attestation not server verified";
        }
        if (evidence.serverAttestationVerifiedAt() == null || evidence.serverAttestationVerifiedAt().isBlank()) {
            return "local evidence device attestation verification time missing";
        }
        String deviceAttestationId = readMetadataText(device.metadataJson(), "deviceAttestationId");
        if (!deviceAttestationId.isBlank() && !equalsText(deviceAttestationId, evidence.deviceAttestationId())) {
            return "local evidence device attestation id not registered";
        }
        String attestationVerdict = readMetadataText(device.metadataJson(), "attestationVerdict");
        if (!attestationVerdict.isBlank() && !equalsText(attestationVerdict, evidence.deviceAttestationVerdict())) {
            return "local evidence device attestation verdict not registered";
        }
        String trustLevel = readMetadataText(device.metadataJson(), "serverVerifiedTrustLevel");
        if (!trustLevel.isBlank() && !equalsText(trustLevel, evidence.serverVerifiedTrustLevel())) {
            return "local evidence device trust level not registered";
        }
        return null;
    }

    private String verifyTransportTranscript(LocalEvidenceSubmission evidence, JsonNode canonical) {
        String transcript = evidence.transportTranscript() == null ? "" : evidence.transportTranscript().trim();
        String source = evidence.transportTranscriptSource() == null
                ? ""
                : evidence.transportTranscriptSource().trim().toUpperCase();
        if (source.startsWith("NATIVE_") && transcript.isBlank()) {
            return "local evidence native transport transcript missing";
        }
        if (transcript.isBlank()) {
            return null;
        }
        if (transcript.length() > 16_384) {
            return "local evidence transport transcript too large";
        }
        String canonicalTranscriptHash = canonical.path("transportTranscriptHash").asText("");
        if (!canonicalTranscriptHash.isBlank() && !equalsText(canonicalTranscriptHash, evidence.transportSessionHash())) {
            return "local evidence canonical transport transcript hash mismatch";
        }
        String encoding = evidence.transportTranscriptEncoding() == null || evidence.transportTranscriptEncoding().isBlank()
                ? "UTF-8"
                : evidence.transportTranscriptEncoding().trim().toUpperCase();
        byte[] transcriptBytes;
        try {
            transcriptBytes = switch (encoding) {
                case "UTF-8", "UTF8", "TEXT" -> transcript.getBytes(StandardCharsets.UTF_8);
                case "BASE64" -> Base64.getDecoder().decode(transcript);
                default -> null;
            };
        } catch (IllegalArgumentException exception) {
            return "local evidence transport transcript base64 invalid";
        }
        if (transcriptBytes == null) {
            return "local evidence transport transcript encoding unsupported";
        }
        if (!equalsText(sha256Hex(transcriptBytes), evidence.transportSessionHash())) {
            return "local evidence transport transcript hash mismatch";
        }
        return null;
    }

    private boolean processMatchingSenderSettlement(String voucherId) {
        return proofRepository.findByVoucherId(voucherId)
                .flatMap(proof -> settlementRepository.findLatestByProofId(proof.id())
                        .map(request -> Map.entry(proof, request)))
                .map(entry -> {
                    OfflinePaymentProof proof = entry.getKey();
                    SettlementRequest request = entry.getValue();
                    if (request.status() == SettlementStatus.PENDING || request.status() == SettlementStatus.VALIDATING) {
                        processSettlementRequest(request, true);
                        return true;
                    }
                    if (shouldRetryFinancialHonorAfterLateReceiverEvidence(proof, request)) {
                        processSettlementRequest(request, true, true);
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private boolean hasRequiredLocalEvidence(LocalEvidenceIngestCommand command, LocalEvidenceSubmission evidence) {
        if (command == null || command.uploaderType() == null || command.uploaderDeviceId() == null || command.uploaderDeviceId().isBlank()) {
            return false;
        }
        String direction = evidence.direction() == null ? "" : evidence.direction().trim().toUpperCase();
        if (!"SEND".equals(direction) && !"RECEIVE".equals(direction)) {
            return false;
        }
        if (command.uploaderType() == UploaderType.SENDER && !"SEND".equals(direction)) {
            return false;
        }
        if (command.uploaderType() == UploaderType.RECEIVER && !"RECEIVE".equals(direction)) {
            return false;
        }
        String expectedUploaderDeviceId = "SEND".equals(direction) ? evidence.senderDeviceId() : evidence.receiverDeviceId();
        if (!equalsText(command.uploaderDeviceId(), expectedUploaderDeviceId)) {
            return false;
        }
        return evidence.voucherId() != null && !evidence.voucherId().isBlank()
                && evidence.senderDeviceId() != null && !evidence.senderDeviceId().isBlank()
                && evidence.receiverDeviceId() != null && !evidence.receiverDeviceId().isBlank()
                && evidence.amount() != null && evidence.amount().compareTo(BigDecimal.ZERO) > 0
                && evidence.counter() != null && evidence.counter() > 0
                && evidence.hashChainHead() != null && !evidence.hashChainHead().isBlank()
                && evidence.nonce() != null && !evidence.nonce().isBlank()
                && evidence.signature() != null && !evidence.signature().isBlank()
                && evidence.canonicalPayload() != null && !evidence.canonicalPayload().isBlank()
                && evidence.deviceAttestationId() != null && !evidence.deviceAttestationId().isBlank()
                && evidence.deviceAttestationVerdict() != null && !evidence.deviceAttestationVerdict().isBlank()
                && evidence.serverVerifiedTrustLevel() != null && !evidence.serverVerifiedTrustLevel().isBlank()
                && evidence.serverAttestationVerifiedAt() != null && !evidence.serverAttestationVerifiedAt().isBlank()
                && evidence.transportTranscriptSource() != null && !evidence.transportTranscriptSource().isBlank();
    }

    private Map<String, Object> buildLocalEvidencePayload(LocalEvidenceSubmission evidence, String direction) {
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        if (evidence.payload() != null) {
            rawPayload.putAll(evidence.payload());
        }
        rawPayload.putIfAbsent("localEvidenceDirectUpload", true);
        rawPayload.putIfAbsent("voucherId", evidence.voucherId());
        rawPayload.putIfAbsent("sessionId", evidence.sessionId());
        rawPayload.putIfAbsent("direction", direction);
        rawPayload.putIfAbsent("senderDeviceId", evidence.senderDeviceId());
        rawPayload.putIfAbsent("receiverDeviceId", evidence.receiverDeviceId());
        rawPayload.putIfAbsent("amount", evidence.amount().toPlainString());
        rawPayload.putIfAbsent("counter", evidence.counter());
        rawPayload.putIfAbsent("prevHash", evidence.previousHash());
        rawPayload.putIfAbsent("newHash", evidence.hashChainHead());
        rawPayload.putIfAbsent("nonce", evidence.nonce());
        rawPayload.putIfAbsent("signature", evidence.signature());
        rawPayload.putIfAbsent("canonicalPayload", evidence.canonicalPayload());
        putIfPresent(rawPayload, "merchantId", evidence.merchantId());
        putIfPresent(rawPayload, "partnerId", evidence.partnerId());
        putIfPresent(rawPayload, "leaderId", evidence.leaderId());
        putIfPresent(rawPayload, "countryCode", upperOrBlank(evidence.countryCode()));
        putIfPresent(rawPayload, "storeId", evidence.storeId());
        putIfPresent(rawPayload, "orderId", evidence.orderId());
        putIfPresent(rawPayload, "paymentIntentId", evidence.paymentIntentId());
        putIfPresent(rawPayload, "invoiceId", evidence.invoiceId());
        putIfPresent(rawPayload, "fiatAmount", evidence.fiatAmount());
        putIfPresent(rawPayload, "fiatCurrency", upperOrBlank(evidence.fiatCurrency()));
        putIfPresent(rawPayload, "exchangeRate", evidence.exchangeRate());
        putIfPresent(rawPayload, "rateTimestamp", evidence.rateTimestamp());
        putIfPresent(rawPayload, "schemaVersion", evidence.schemaVersion());
        putIfPresent(rawPayload, "protocolVersion", evidence.protocolVersion());
        putIfPresent(rawPayload, "hashAlgorithm", evidence.hashAlgorithm());
        putIfPresent(rawPayload, "signatureAlgorithm", evidence.signatureAlgorithm());
        putIfPresent(rawPayload, "keyId", evidence.keyId());
        putIfPresent(rawPayload, "publicKeyFingerprint", evidence.publicKeyFingerprint());
        putIfPresent(rawPayload, "appVersion", evidence.appVersion());
        putIfPresent(rawPayload, "deviceAttestationId", evidence.deviceAttestationId());
        putIfPresent(rawPayload, "deviceAttestationVerdict", evidence.deviceAttestationVerdict());
        putIfPresent(rawPayload, "serverVerifiedTrustLevel", evidence.serverVerifiedTrustLevel());
        putIfPresent(rawPayload, "serverAttestationVerifiedAt", evidence.serverAttestationVerifiedAt());
        putIfPresent(rawPayload, "transportSessionHash", evidence.transportSessionHash());
        putIfPresent(rawPayload, "transportTranscriptSource", evidence.transportTranscriptSource());
        putIfPresent(rawPayload, "transportTranscript", evidence.transportTranscript());
        putIfPresent(rawPayload, "transportTranscriptEncoding", evidence.transportTranscriptEncoding());
        return rawPayload;
    }

    private String readMetadataText(String metadataJson, String fieldName) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        JsonNode root = jsonService.readTree(metadataJson);
        if (!root.isObject()) {
            return "";
        }
        JsonNode direct = root.path(fieldName);
        if (direct.isTextual() && !direct.asText().isBlank()) {
            return direct.asText().trim();
        }
        JsonNode attestation = root.path("attestation");
        JsonNode nested = attestation.path(fieldName);
        return nested.isTextual() ? nested.asText().trim() : "";
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.putIfAbsent(key, value.trim());
        }
    }

    private String upperOrBlank(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizePublicKeyMaterial(String value) {
        return value == null
                ? ""
                : value.trim()
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s+", "");
    }

    private SettlementBatch createBatch(SubmitSettlementBatchCommand command) {
        for (ProofSubmission submission : command.proofs()) {
            assertLocalBlockMatchesSubmission(submission);
            OfflinePaymentProof existing = proofRepository.findByVoucherId(submission.voucherId()).orElse(null);
            if (existing != null && isReceiverConfirmation(command, submission, existing)) {
                continue;
            }
            assertProofSubmissionNotReplayed(submission);
            if (command.uploaderType() == UploaderType.RECEIVER && existing == null && hasReceiverLocalBlock(submission)) {
                throw new IllegalArgumentException("receiver settlement requires existing sender proof: " + submission.voucherId());
            }
        }

        SettlementBatchFactory.SettlementBatchDraft batchDraft = settlementBatchFactory.createDraft(command);
        SettlementBatch batch = batchRepository.save(
                batchDraft.sourceDeviceId(),
                batchDraft.idempotencyKey(),
                batchDraft.status(),
                null,
                batchDraft.proofsCount(),
                batchDraft.summaryJson()
        );
        if (batch.status() != SettlementBatchStatus.CREATED) {
            return batch;
        }

        List<String> requestIds = new ArrayList<>();
        int receiverConfirmationCount = 0;
        for (ProofSubmission submission : command.proofs()) {
            OfflinePaymentProof existing = proofRepository.findByVoucherId(submission.voucherId()).orElse(null);
            if (existing != null && isReceiverConfirmation(command, submission, existing)) {
                String existingProofId = existing.id();
                SettlementRequest request = settlementRepository.findLatestByProofId(existing.id())
                        .orElseThrow(() -> new IllegalStateException("settlement request not found for proof: " + existingProofId));
                saveLocalEvidence(command, submission, existing.id(), "VERIFIED", "matched receiver evidence");
                boolean retryFinancialHonor = shouldRetryFinancialHonorAfterLateReceiverEvidence(existing, request);
                if (request.status() == SettlementStatus.PENDING
                        || request.status() == SettlementStatus.VALIDATING
                        || retryFinancialHonor) {
                    processSettlementRequest(request, true, retryFinancialHonor);
                    request = settlementRepository.findLatestByProofId(existing.id())
                            .orElseThrow(() -> new IllegalStateException("settlement request not found for proof: " + existingProofId));
                    existing = proofRepository.findById(existing.id())
                            .orElse(existing);
                }
                preserveReceivedUnsettledAmount(existing, request);
                if (shouldAutoConfirmReceiverSettlement(submission)) {
                    handleReceiverOnlineConfirmation(existing, request);
                }
                requestIds.add(request.id());
                receiverConfirmationCount++;
                continue;
            }

            CollateralLock collateral = collateralRepository.findById(submission.collateralId())
                    .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + submission.collateralId()));
            Device receiverDevice = deviceIdentifierResolver.resolve(submission.receiverDeviceId())
                    .orElseThrow(() -> new IllegalArgumentException("receiver device not found: " + submission.receiverDeviceId()));
            long senderUserId = resolveSenderUserId(submission, collateral);
            long receiverUserId = resolveReceiverUserId(submission, receiverDevice);

            OfflinePaymentProof proof = proofRepository.save(
                    batch.id(),
                    submission.voucherId(),
                    submission.collateralId(),
                    submission.senderDeviceId(),
                    submission.receiverDeviceId(),
                    senderUserId,
                    receiverUserId,
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
            saveLocalEvidence(command, submission, proof.id(), "PENDING", "sender evidence stored before receiver match");

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

        if (receiverConfirmationCount == command.proofs().size()) {
            batchRepository.updateStatus(
                    batch.id(),
                    SettlementBatchStatus.SETTLED,
                    OfflinePayReasonCode.SETTLED,
                    jsonService.write(Map.of(
                            "requestIds", requestIds,
                            "triggerMode", command.triggerMode() == null || command.triggerMode().isBlank() ? "MANUAL" : command.triggerMode(),
                            "receiverConfirmationCount", receiverConfirmationCount,
                            "finalizedAt", OffsetDateTime.now().toString()
                    ))
            );
            return batchRepository.findById(batch.id()).orElseThrow();
        }

        batchRepository.updateStatus(
                batch.id(),
                SettlementBatchStatus.UPLOADED,
                null,
                settlementBatchFactory.uploadedSummary(requestIds, command.triggerMode())
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

    private void assertLocalBlockMatchesSubmission(ProofSubmission submission) {
        assertLocalBlockMatchesSubmission(submission, "senderLocalBlock");
        assertLocalBlockMatchesSubmission(submission, "receiverLocalBlock");
        assertReceiverEvidenceBlockMatchesSubmission(submission);
    }

    private boolean hasReceiverLocalBlock(ProofSubmission submission) {
        Map<String, Object> payload = submission.payload();
        return payload != null && (Boolean.TRUE.equals(payload.get("receiverLocalBlock"))
                || Boolean.TRUE.equals(payload.get("receiverEvidenceBlock")));
    }

    private void saveLocalEvidence(
            SubmitSettlementBatchCommand command,
            ProofSubmission submission,
            String proofId,
            String verificationStatus,
            String verificationDetail
    ) {
        Map<String, Object> payload = submission.payload();
        if (payload == null) {
            return;
        }
        boolean senderEvidence = Boolean.TRUE.equals(payload.get("senderLocalBlock"))
                || !stringValue(payload.get("localBlockNewHash")).isBlank();
        boolean receiverEvidence = Boolean.TRUE.equals(payload.get("receiverEvidenceBlock"))
                || Boolean.TRUE.equals(payload.get("receiverLocalBlock"));
        if (!senderEvidence && !receiverEvidence) {
            return;
        }
        String direction = receiverEvidence ? "RECEIVE" : "SEND";
        String prefix = receiverEvidence
                ? (Boolean.TRUE.equals(payload.get("receiverEvidenceBlock")) ? "receiverEvidenceBlock" : "receiverLocalBlock")
                : "senderLocalBlock";
        String uploaderDeviceId = command.uploaderDeviceId() == null || command.uploaderDeviceId().isBlank()
                ? ("RECEIVE".equals(direction) ? submission.receiverDeviceId() : submission.senderDeviceId())
                : command.uploaderDeviceId();
        String evidenceNonce = firstText(payload, prefix + "Nonce", "localBlockNonce", "nonce", "requestId", "offlineTxSequence");
        if (evidenceNonce.isBlank()) {
            evidenceNonce = submission.nonce();
        }
        localEvidenceRepository.save(new OfflinePayLocalEvidence(
                proofId,
                submission.voucherId(),
                firstText(payload, prefix + "SessionId", "receiverLocalBlockSessionId", "senderLocalBlockSessionId", "requestId"),
                direction,
                command.uploaderType().name(),
                uploaderDeviceId,
                submission.senderDeviceId(),
                submission.receiverDeviceId(),
                submission.amount(),
                firstPositiveLong(payload, prefix + "Counter", "localBlockCounter"),
                firstText(payload, prefix + "PrevHash", "localBlockPrevHash"),
                firstText(payload, prefix + "NewHash", "localBlockNewHash"),
                evidenceNonce,
                firstText(payload, prefix + "Signature", "localBlockSignature"),
                firstText(payload, prefix + "CanonicalPayload", "localBlockCanonicalPayload"),
                payload,
                verificationStatus == null || verificationStatus.isBlank() ? "PENDING" : verificationStatus,
                verificationDetail,
                proofId
        ));
    }

    private void assertLocalBlockMatchesSubmission(ProofSubmission submission, String prefix) {
        Map<String, Object> payload = submission.payload();
        if (payload == null || !Boolean.TRUE.equals(payload.get(prefix))) {
            return;
        }
        assertLocalBlockText(prefix, "VoucherId", submission.voucherId(), payload.get(prefix + "VoucherId"));
        assertLocalBlockText(prefix, "SenderDeviceId", submission.senderDeviceId(), payload.get(prefix + "SenderDeviceId"));
        assertLocalBlockText(prefix, "ReceiverDeviceId", submission.receiverDeviceId(), payload.get(prefix + "ReceiverDeviceId"));
        assertLocalBlockLong(prefix, "Counter", submission.counter(), payload.get(prefix + "Counter"));
        assertLocalBlockText(prefix, "PrevHash", submission.previousHash(), payload.get(prefix + "PrevHash"));
        assertLocalBlockText(prefix, "NewHash", submission.hashChainHead(), payload.get(prefix + "NewHash"));
        assertLocalBlockText(prefix, "Nonce", submission.nonce(), payload.get(prefix + "Nonce"));
        assertLocalBlockText(prefix, "Signature", submission.signature(), payload.get(prefix + "Signature"));
        assertLocalBlockAmount(prefix, submission.amount(), payload.get(prefix + "Amount"));
    }

    private void assertReceiverEvidenceBlockMatchesSubmission(ProofSubmission submission) {
        Map<String, Object> payload = submission.payload();
        if (payload == null || !Boolean.TRUE.equals(payload.get("receiverEvidenceBlock"))) {
            return;
        }

        String prefix = "receiverEvidenceBlock";
        String canonicalPayload = stringValue(payload.get(prefix + "CanonicalPayload"));
        if (canonicalPayload.isBlank()) {
            throw new IllegalArgumentException(prefix + " CanonicalPayload missing");
        }
        String submittedHash = stringValue(payload.get(prefix + "NewHash"));
        if (!equalsText(sha256Hex(canonicalPayload), submittedHash)) {
            throw new IllegalArgumentException(prefix + " NewHash mismatch");
        }
        long blockCounter = requirePositiveLong(prefix, "Counter", payload.get(prefix + "Counter"));
        assertLocalBlockText(prefix, "SenderProofNewHash", submission.hashChainHead(), payload.get(prefix + "SenderProofNewHash"));
        assertLocalBlockText(prefix, "SenderProofPrevHash", submission.previousHash(), payload.get(prefix + "SenderProofPrevHash"));
        assertLocalBlockText(prefix, "SenderProofNonce", submission.nonce(), payload.get(prefix + "SenderProofNonce"));
        assertLocalBlockText(prefix, "SenderProofSignature", submission.signature(), payload.get(prefix + "SenderProofSignature"));
        assertLocalBlockLong(prefix, "SenderProofCounter", submission.counter(), payload.get(prefix + "SenderProofCounter"));

        JsonNode canonical = jsonService.readTree(canonicalPayload);
        assertLocalBlockText(prefix, "CanonicalVoucherId", submission.voucherId(), canonical.path("voucherId").asText(""));
        assertLocalBlockText(prefix, "CanonicalDirection", "RECEIVE", canonical.path("direction").asText(""));
        assertLocalBlockText(prefix, "CanonicalDeviceId", submission.receiverDeviceId(), canonical.path("deviceId").asText(""));
        assertLocalBlockText(prefix, "CanonicalSenderDeviceId", submission.senderDeviceId(), canonical.path("senderDeviceId").asText(""));
        assertLocalBlockText(prefix, "CanonicalReceiverDeviceId", submission.receiverDeviceId(), canonical.path("receiverDeviceId").asText(""));
        assertLocalBlockText(prefix, "CanonicalPrevHash", stringValue(payload.get(prefix + "PrevHash")), canonical.path("prevHash").asText(""));
        assertLocalBlockText(prefix, "CanonicalNonce", stringValue(payload.get(prefix + "Nonce")), canonical.path("nonce").asText(""));
        assertLocalBlockLong(prefix, "CanonicalCounter", blockCounter, canonical.path("counter").asLong(-1));
        assertLocalBlockAmount(prefix, submission.amount(), canonical.path("amount").asText(""));

        Device receiverDevice = deviceIdentifierResolver.resolve(submission.receiverDeviceId())
                .orElseThrow(() -> new IllegalArgumentException(prefix + " receiver device not found"));
        DeviceSignatureVerificationService.VerificationResult verification = deviceSignatureVerificationService.verifyPayload(
                receiverDevice,
                canonicalPayload,
                stringValue(payload.get(prefix + "Signature"))
        );
        if (!verification.verified()) {
            throw new IllegalArgumentException(prefix + " signature invalid: " + verification.detail());
        }
    }

    private void assertLocalBlockText(String prefix, String field, String expected, Object actual) {
        if (!equalsText(expected, actual == null ? "" : String.valueOf(actual))) {
            throw new IllegalArgumentException(prefix + " " + field + " mismatch");
        }
    }

    private void assertLocalBlockLong(String prefix, String field, long expected, Object actual) {
        Long parsed = parsePositiveLong(actual);
        if (parsed == null || parsed != expected) {
            throw new IllegalArgumentException(prefix + " " + field + " mismatch");
        }
    }

    private long requirePositiveLong(String prefix, String field, Object actual) {
        Long parsed = parsePositiveLong(actual);
        if (parsed == null) {
            throw new IllegalArgumentException(prefix + " " + field + " mismatch");
        }
        return parsed;
    }

    private void assertLocalBlockAmount(String prefix, BigDecimal expected, Object actual) {
        BigDecimal parsed = parsePositiveDecimal(actual);
        if (parsed == null || parsed.compareTo(expected) != 0) {
            throw new IllegalArgumentException(prefix + " Amount mismatch");
        }
    }

    private boolean amountEquals(BigDecimal expected, Object actual) {
        BigDecimal parsed = parsePositiveDecimal(actual);
        return parsed != null && parsed.compareTo(expected) == 0;
    }

    private Long parsePositiveLong(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                long parsed = Long.parseLong(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long parseNonNegativeLong(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed >= 0 ? parsed : null;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                long parsed = Long.parseLong(text.trim());
                return parsed >= 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstText(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = stringValue(payload.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Long firstPositiveLong(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Long value = parsePositiveLong(payload.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long firstEpochLong(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Long value = parseNonNegativeLong(payload.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer firstPositiveInteger(Map<String, Object> payload, String... keys) {
        Long value = firstPositiveLong(payload, keys);
        if (value == null || value > Integer.MAX_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private String sha256Hex(String value) {
        return sha256Hex((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value == null ? new byte[0] : value);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private BigDecimal parsePositiveDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.compareTo(BigDecimal.ZERO) > 0 ? decimal : null;
        }
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString());
            return decimal.compareTo(BigDecimal.ZERO) > 0 ? decimal : null;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                BigDecimal decimal = new BigDecimal(text.trim());
                return decimal.compareTo(BigDecimal.ZERO) > 0 ? decimal : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void preserveReceivedUnsettledAmount(OfflinePaymentProof proof, SettlementRequest request) {
        if (isReceiverSettlementConfirmable(proof, request)) {
            proofRepository.ensureReceivedUnsettledAmount(proof.id(), calculateReceiverAmount(proof, resolveProofAssetCode(proof)));
        }
    }

    private boolean shouldAutoConfirmReceiverSettlement(ProofSubmission submission) {
        Map<String, Object> payload = submission.payload();
        if (payload == null) {
            return false;
        }
        Object autoEnabled = payload.get("receiverSettlementAutoEnabled");
        if (Boolean.TRUE.equals(autoEnabled)) {
            return true;
        }
        Object mode = payload.get("receiverSettlementMode");
        return mode instanceof String text && "AUTO".equalsIgnoreCase(text.trim());
    }

    private boolean shouldAutoConfirmReceiverSettlement(OfflinePaymentProof proof) {
        JsonNode payload = jsonService.readTree(proof.rawPayloadJson());
        if (payload.path("receiverSettlementAutoEnabled").asBoolean(false)) {
            return true;
        }
        return "AUTO".equalsIgnoreCase(payload.path("receiverSettlementMode").asText(""));
    }

    private BigDecimal calculateReceiverAmount(OfflinePaymentProof proof, String assetCode) {
        return feeCalculator.calculateReceiverAmount(assetCode, proof.amount());
    }

    private String resolveProofAssetCode(OfflinePaymentProof proof) {
        JsonNode payload = jsonService.readTree(proof.rawPayloadJson());
        String token = payload.path("token").asText("");
        if (!token.isBlank()) {
            return token;
        }
        String assetCode = payload.path("assetCode").asText("");
        return assetCode.isBlank() ? "KORI" : assetCode;
    }

    private void assertProofSubmissionNotReplayed(ProofSubmission submission) {
        proofRepository.findByVoucherId(submission.voucherId())
                .ifPresent(existing -> {
                    throw duplicateProofSubmissionException("voucherId", existing);
                });
        if (submission.nonce() != null && !submission.nonce().isBlank()) {
            proofRepository.findBySenderNonce(submission.senderDeviceId(), submission.nonce().trim())
                    .ifPresent(existing -> {
                        throw duplicateProofSubmissionException("nonce", existing);
                    });
        }
        Long offlineTxSequence = extractOfflineTxSequence(submission.payload());
        if (offlineTxSequence != null) {
            proofRepository.findBySenderOfflineTxSequence(submission.senderDeviceId(), offlineTxSequence)
                    .ifPresent(existing -> {
                        throw duplicateProofSubmissionException("offlineTxSequence", existing);
                    });
        }
        String requestId = extractRequestId(submission.payload());
        if (requestId != null) {
            proofRepository.findBySenderRequestId(submission.senderDeviceId(), requestId)
                    .ifPresent(existing -> {
                        throw duplicateProofSubmissionException("requestId", existing);
                    });
        }
    }

    private IllegalArgumentException duplicateProofSubmissionException(String field, OfflinePaymentProof existing) {
        return new IllegalArgumentException(
                "duplicate offline proof submission by " + field + ": proofId=" + existing.id()
        );
    }

    private Long extractOfflineTxSequence(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get("offlineTxSequence");
        if (value instanceof Number number) {
            long sequence = number.longValue();
            return sequence > 0 ? sequence : null;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                long sequence = Long.parseLong(text.trim());
                return sequence > 0 ? sequence : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isReceiverConfirmation(
            SubmitSettlementBatchCommand command,
            ProofSubmission submission,
            OfflinePaymentProof existing
    ) {
        if (command.uploaderType() != UploaderType.RECEIVER) {
            return false;
        }
        if (!"SENDER".equalsIgnoreCase(existing.uploaderType())) {
            return false;
        }
        return equalsText(existing.voucherId(), submission.voucherId())
                && equalsText(existing.collateralId(), submission.collateralId())
                && equalsText(existing.senderDeviceId(), submission.senderDeviceId())
                && equalsText(existing.receiverDeviceId(), submission.receiverDeviceId())
                && equalsText(existing.hashChainHead(), submission.hashChainHead())
                && equalsText(existing.previousHash(), submission.previousHash())
                && equalsText(existing.signature(), submission.signature())
                && equalsText(existing.nonce(), submission.nonce())
                && existing.monotonicCounter() == submission.counter()
                && existing.amount().compareTo(submission.amount()) == 0;
    }

    private boolean equalsText(String left, String right) {
        return String.valueOf(left == null ? "" : left).equals(String.valueOf(right == null ? "" : right));
    }

    private void handleReceiverOnlineConfirmation(OfflinePaymentProof proof, SettlementRequest request) {
        boolean financiallyHonored = isFinanciallyHonoredSettlement(proof, request);
        if (proof.status() != OfflineProofStatus.SETTLED && !financiallyHonored) {
            return;
        }
        if (request.status() != SettlementStatus.SETTLED && !financiallyHonored) {
            return;
        }
        OfflineSaga saga = offlineSagaRepository
                .findBySagaTypeAndReferenceId(OfflineSagaType.SETTLEMENT, request.id())
                .orElse(null);
        if (saga != null && shouldIgnoreLateReceiverConfirmation(saga)) {
            if (hasReceiverHistorySynced(saga) && hasReceivedUnsettledAmount(proof)) {
                proofRepository.markReceivedCollateralSettled(
                        List.of(proof.id()),
                        null,
                        "wallet:" + request.id()
                );
            }
            return;
        }
        Device receiverDevice = resolveCurrentReceiverDeviceForProofOwner(proof).orElse(null);
        if (receiverDevice == null) {
            return;
        }
        CollateralLock collateral = collateralRepository.findById(request.collateralId())
                .orElseThrow(() -> new IllegalStateException("collateral not found for settlement: " + request.id()));
        CoinManageSettlementPort.SettlementLedgerCommand receiverLedgerCommand = settlementSyncCommandFactory.createLedgerCommand(
                collateral,
                proof,
                proof.amount(),
                request,
                request.status().name(),
                "RELEASE",
                false,
                receiverDevice,
                true,
                financiallyHonored
        );
        CoinManageSettlementPort.SettlementLedgerResult receiverLedgerResult =
                coinManageSettlementPort.finalizeSettlement(receiverLedgerCommand);
        FoxCoinHistoryPort.SettlementHistoryCommand receiverHistoryCommand = settlementSyncCommandFactory.createReceiverHistoryCommand(
                collateral,
                proof.id(),
                proof.amount(),
                request,
                request.status().name(),
                "RELEASE",
                receiverDevice
        );
        eventBus.publishExternalSyncRequested(
                "RECEIVER_HISTORY_SYNC_REQUESTED",
                request.id(),
                request.batchId(),
                proof.id(),
                jsonService.write(Map.of(
                        "settlementId", request.id(),
                        "batchId", request.batchId(),
                        "proofId", proof.id(),
                        "receiverWalletSettlementRequested", true,
                        "receiverLedgerOutcome", receiverLedgerResult.ledgerOutcome(),
                        "receiverSettlementMode", receiverLedgerResult.receiverSettlementMode(),
                        "receiverOnlineConfirmedAt", OffsetDateTime.now().toString(),
                        "receiverHistoryCommand", Map.ofEntries(
                                Map.entry("settlementId", receiverHistoryCommand.settlementId()),
                                Map.entry("transferRef", receiverHistoryCommand.transferRef()),
                                Map.entry("batchId", receiverHistoryCommand.batchId()),
                                Map.entry("collateralId", receiverHistoryCommand.collateralId()),
                                Map.entry("proofId", receiverHistoryCommand.proofId()),
                                Map.entry("userId", receiverHistoryCommand.userId()),
                                Map.entry("deviceId", receiverHistoryCommand.deviceId()),
                                Map.entry("assetCode", receiverHistoryCommand.assetCode()),
                                Map.entry("amount", receiverHistoryCommand.amount()),
                                Map.entry("feeAmount", receiverHistoryCommand.feeAmount()),
                                Map.entry("settlementStatus", receiverHistoryCommand.settlementStatus()),
                                Map.entry("historyType", receiverHistoryCommand.historyType())
                        )
                )),
                OffsetDateTime.now().toString()
        );
        offlineSagaService.markPartiallyApplied(
                OfflineSagaType.SETTLEMENT,
                request.id(),
                "HISTORY_SYNCED",
                Map.of(
                        "settlementId", request.id(),
                        "batchId", request.batchId(),
                        "proofId", proof.id(),
                        "senderHistorySynced", true,
                        "receiverHistoryPending", true,
                        "receiverOnlineConfirmed", true,
                        "receiverWalletSettlementRequested", true
                )
        );
    }

    private boolean proofBelongsToReceiverUser(OfflinePaymentProof proof, long userId) {
        return proof.receiverUserId() != null && proof.receiverUserId() == userId;
    }

    private long resolveSenderUserId(ProofSubmission submission, CollateralLock collateral) {
        Long payloadSenderUserId = positiveLongPayloadValue(submission.payload(), "senderUserId");
        if (payloadSenderUserId != null && payloadSenderUserId != collateral.userId()) {
            throw new IllegalArgumentException("sender user mismatch for proof: " + submission.voucherId());
        }
        return collateral.userId();
    }

    private long resolveReceiverUserId(ProofSubmission submission, Device receiverDevice) {
        Long payloadReceiverUserId = positiveLongPayloadValue(submission.payload(), "receiverUserId");
        return payloadReceiverUserId != null ? payloadReceiverUserId : receiverDevice.userId();
    }

    private Long positiveLongPayloadValue(Map<String, Object> payload, String key) {
        if (payload == null || key == null || key.isBlank()) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            long longValue = number.longValue();
            return longValue > 0 ? longValue : null;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.matches("^[0-9]+$")) {
                long longValue = Long.parseLong(normalized);
                return longValue > 0 ? longValue : null;
            }
        }
        return null;
    }

    private Optional<Device> resolveCurrentReceiverDeviceForProofOwner(OfflinePaymentProof proof) {
        if (proof.receiverUserId() == null) {
            return Optional.empty();
        }
        return deviceIdentifierResolver.resolve(proof.receiverDeviceId())
                .filter(device -> device.userId() == proof.receiverUserId());
    }

    private boolean shouldIgnoreLateReceiverConfirmation(OfflineSaga saga) {
        if (hasReceiverHistorySynced(saga)) {
            return true;
        }
        return saga.status() == OfflineSagaStatus.COMPENSATION_REQUIRED
                || saga.status() == OfflineSagaStatus.COMPENSATING
                || saga.status() == OfflineSagaStatus.COMPENSATED
                || OfflinePayReasonCode.RECEIVER_CONFIRMATION_EXPIRED.equals(saga.lastReasonCode());
    }

    private boolean hasReceiverHistorySynced(OfflineSaga saga) {
        return saga != null && "RECEIVER_HISTORY_SYNCED".equals(saga.currentStep());
    }

    private boolean hasReceivedUnsettledAmount(OfflinePaymentProof proof) {
        return proof.receivedUnsettledAmount() != null
                && proof.receivedUnsettledAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isReceiverSettlementConfirmable(OfflinePaymentProof proof, SettlementRequest request) {
        if (proof == null || request == null) {
            return false;
        }
        if (proof.status() == OfflineProofStatus.SETTLED && request.status() == SettlementStatus.SETTLED) {
            return true;
        }
        return isFinanciallyHonoredSettlement(proof, request);
    }

    private boolean isFinanciallyHonoredSettlement(OfflinePaymentProof proof, SettlementRequest request) {
        return proof != null
                && request != null
                && request.status() == SettlementStatus.REJECTED
                && !request.conflictDetected()
                && isFinanciallyHonored(request.settlementResultJson());
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
        int pendingCount = 0;
        boolean hasConflict = false;
        for (SettlementRequest request : requests) {
            SettlementEvaluation evaluation = processSettlementRequest(request);
            if (evaluation.status() == SettlementStatus.SETTLED) {
                settledCount++;
            } else if (evaluation.status() == SettlementStatus.PENDING || evaluation.status() == SettlementStatus.VALIDATING) {
                pendingCount++;
            } else {
                failedCount++;
            }
            hasConflict = hasConflict || evaluation.conflictDetected();
        }

        SettlementBatchStatus batchStatus = resolveBatchStatus(settledCount, failedCount, pendingCount, hasConflict);
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
                        "pendingCount", pendingCount,
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

    @Transactional(readOnly = true)
    public SettlementBatchDetailView getBatchDetail(String batchId) {
        SettlementBatch batch = getBatch(batchId);
        List<SettlementDetailView> settlements = settlementRepository.findByBatchId(batchId).stream()
                .map(request -> {
                    OfflineSaga settlementSaga = offlineSagaRepository
                            .findBySagaTypeAndReferenceId(OfflineSagaType.SETTLEMENT, request.id())
                            .orElse(null);
                    ReconciliationCase reconciliationCase = reconciliationCaseRepository
                            .findLatestOpenBySettlementId(request.id())
                            .orElse(null);
                    OfflinePaymentProof proof = proofRepository.findById(request.proofId())
                            .orElse(null);
                    CollateralLock collateral = collateralRepository.findById(request.collateralId())
                            .orElse(null);
                    return new SettlementDetailView(request, settlementSaga, reconciliationCase, proof, collateral);
                })
                .toList();
        return new SettlementBatchDetailView(batch, settlements);
    }

    @Transactional
    public SettlementRequest finalizeSettlement(String settlementId) {
        SettlementRequest request = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("settlement not found: " + settlementId));
        processSettlementRequest(request);
        return settlementRepository.findById(settlementId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public SettlementRequest getSettlement(String settlementId) {
        return settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("settlement not found: " + settlementId));
    }

    @Transactional(readOnly = true)
    public SettlementDetailView getSettlementDetail(String settlementId) {
        SettlementRequest settlementRequest = getSettlement(settlementId);
        OfflineSaga settlementSaga = offlineSagaRepository
                .findBySagaTypeAndReferenceId(OfflineSagaType.SETTLEMENT, settlementId)
                .orElse(null);
        ReconciliationCase reconciliationCase = reconciliationCaseRepository
                .findLatestOpenBySettlementId(settlementId)
                .orElse(null);
        OfflinePaymentProof proof = proofRepository.findById(settlementRequest.proofId())
                .orElse(null);
        CollateralLock collateral = collateralRepository.findById(settlementRequest.collateralId())
                .orElse(null);
        return new SettlementDetailView(settlementRequest, settlementSaga, reconciliationCase, proof, collateral);
    }

    @Transactional
    public BatchFailureOutcome recordBatchProcessingFailure(String batchId, String errorMessage, int maxAttempts) {
        SettlementBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("settlement batch not found: " + batchId));
        int attemptCount = currentAttemptCount(batch.summaryJson()) + 1;

        batchRepository.updateStatus(
                batch.id(),
                batch.status(),
                normalizeBatchReasonCode(batch.status(), OfflinePayReasonCode.BATCH_SYNC_FAIL),
                settlementBatchFactory.failureSummary(attemptCount, errorMessage, OfflinePayReasonCode.BATCH_SYNC_FAIL)
        );
        return new BatchFailureOutcome(batch.id(), attemptCount, false);
    }

    private SettlementEvaluation processSettlementRequest(SettlementRequest request) {
        return processSettlementRequest(request, false);
    }

    private SettlementEvaluation processSettlementRequest(SettlementRequest request, boolean receiverEvidenceMatched) {
        return processSettlementRequest(request, receiverEvidenceMatched, false);
    }

    private SettlementEvaluation processSettlementRequest(
            SettlementRequest request,
            boolean receiverEvidenceMatched,
            boolean localCompletedRejectedRetry
    ) {
        OfflinePaymentProof proof = proofRepository.findById(request.proofId())
                .orElseThrow(() -> new IllegalArgumentException("proof not found: " + request.proofId()));
        CollateralLock collateral = collateralRepository.findById(request.collateralId())
                .orElseThrow(() -> new IllegalArgumentException("collateral not found: " + request.collateralId()));
        offlineSagaService.markProcessing(
                OfflineSagaType.SETTLEMENT,
                request.id(),
                "VALIDATING",
                Map.of(
                        "settlementId", request.id(),
                        "batchId", request.batchId(),
                        "proofId", proof.id(),
                        "collateralId", collateral.id(),
                        "voucherId", proof.voucherId()
                )
        );

        boolean validationStartedFromPending = isServerValidationPending(request) || localCompletedRejectedRetry;
        boolean receiverEvidenceConfirmed = markVerifiedEvidenceForProof(proof, receiverEvidenceMatched);

        proofRepository.updateLifecycle(proof.id(), OfflineProofStatus.CONSUMED_PENDING_SETTLEMENT, null, true, false, false);
        CollateralLock settlementCollateralScope = resolveSettlementCollateralScope(collateral, proof);
        SettlementEvaluation evaluation = evaluateProof(proof, settlementCollateralScope, request.id());
        boolean senderAuthorizationCompleted = hasCompletedSenderAuthorization(proof);
        boolean financiallyHonored = shouldFinanciallyHonorLocalPendingSettlement(
                validationStartedFromPending,
                receiverEvidenceConfirmed,
                senderAuthorizationCompleted,
                evaluation
        );
        String evaluatedReasonCode = normalizeSettlementReasonCode(evaluation.status(), evaluation.reasonCode(), evaluation.conflictDetected());
        String terminalReasonCode = preserveFinancialHonorReasonCode(
                localCompletedRejectedRetry,
                financiallyHonored,
                request.reasonCode(),
                evaluatedReasonCode
        );
        String settlementResultJson = settlementResultJson(
                evaluation,
                financiallyHonored,
                receiverEvidenceConfirmed,
                senderAuthorizationCompleted,
                validationStartedFromPending,
                terminalReasonCode
        );
        proofRepository.updateLifecycle(
                proof.id(),
                mapProofStatus(evaluation.status(), evaluation.conflictDetected()),
                normalizeProofReasonCode(mapProofStatus(evaluation.status(), evaluation.conflictDetected()), terminalReasonCode),
                true,
                evaluation.status() == SettlementStatus.SETTLED,
                evaluation.status() == SettlementStatus.SETTLED
        );
        if (evaluation.status() == SettlementStatus.SETTLED || financiallyHonored) {
            proofRepository.ensureReceivedUnsettledAmount(
                    proof.id(),
                    calculateReceiverAmount(proof, settlementCollateralScope.assetCode())
            );
            deductSettlementAmountAcrossCollateralScope(collateral, proof, request, evaluation.status(), financiallyHonored);
        }
        settlementRepository.update(
                request.id(),
                evaluation.status(),
                terminalReasonCode,
                evaluation.conflictDetected(),
                settlementResultJson
        );
        settlementResultRepository.save(
                request.id(),
                request.batchId(),
                proof,
                evaluation.status(),
                terminalReasonCode,
                settlementResultJson,
                financiallyHonored ? proof.amount() : evaluation.settledAmount()
        );
        saveReconciliationCase(request, proof, evaluation, settlementResultJson);
        if (evaluation.status() != SettlementStatus.SETTLED && !financiallyHonored) {
            collateralRepository.updateStatus(
                    collateral.id(),
                    resolveCollateralStatus(collateral, proof, evaluation),
                    jsonService.write(Map.of(
                            "lastSettlementId", request.id(),
                            "lastVoucherId", proof.voucherId(),
                            "lastStatus", evaluation.status().name()
                    ))
            );
        }

        if (evaluation.status() == SettlementStatus.SETTLED || evaluation.conflictDetected() || financiallyHonored) {
            offlineSagaService.markProcessing(
                    OfflineSagaType.SETTLEMENT,
                    request.id(),
                    "EXTERNAL_SYNC_REQUESTED",
                    Map.of(
                            "settlementId", request.id(),
                            "batchId", request.batchId(),
                            "proofId", proof.id(),
                            "collateralId", collateral.id(),
                            "reasonCode", terminalReasonCode,
                            "financiallyHonored", financiallyHonored
                    )
            );
            syncExternalSettlement(collateral, proof, request, evaluation, financiallyHonored);
        } else {
            offlineSagaService.markFailed(
                    OfflineSagaType.SETTLEMENT,
                    request.id(),
                    "SETTLEMENT_REJECTED",
                    terminalReasonCode,
                    Map.of(
                            "settlementId", request.id(),
                            "batchId", request.batchId(),
                            "proofId", proof.id(),
                            "collateralId", collateral.id(),
                            "reasonCode", terminalReasonCode,
                            "status", evaluation.status().name()
                    )
            );
        }
        return evaluation;
    }

    private boolean isServerValidationPending(SettlementRequest request) {
        return request.status() == SettlementStatus.PENDING || request.status() == SettlementStatus.VALIDATING;
    }

    private boolean shouldFinanciallyHonorLocalPendingSettlement(
            boolean validationStartedFromPending,
            boolean receiverEvidenceConfirmed,
            boolean senderAuthorizationCompleted,
            SettlementEvaluation evaluation
    ) {
        return validationStartedFromPending
                && receiverEvidenceConfirmed
                && senderAuthorizationCompleted
                && evaluation.status() == SettlementStatus.REJECTED
                && !evaluation.conflictDetected();
    }

    private boolean shouldRetryFinancialHonorAfterLateReceiverEvidence(
            OfflinePaymentProof proof,
            SettlementRequest request
    ) {
        if (request.status() != SettlementStatus.REJECTED || request.conflictDetected()) {
            return false;
        }
        if (isFinanciallyHonored(request.settlementResultJson())) {
            return false;
        }
        return hasCompletedSenderAuthorization(proof)
                && localEvidenceRepository.existsMatchingReceiverEvidence(proof);
    }

    private boolean isFinanciallyHonored(String settlementResultJson) {
        if (settlementResultJson == null || settlementResultJson.isBlank()) {
            return false;
        }
        return jsonService.readTree(settlementResultJson).path("financiallyHonored").asBoolean(false);
    }

    private String settlementResultJson(
            SettlementEvaluation evaluation,
            boolean financiallyHonored,
            boolean receiverEvidenceConfirmed,
            boolean senderAuthorizationCompleted,
            boolean validationStartedFromPending,
            String terminalReasonCode
    ) {
        if (!financiallyHonored) {
            return evaluation.resultJson();
        }
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>(jsonService.readMap(evaluation.resultJson()));
        result.put("reasonCode", terminalReasonCode);
        result.put("evaluatedReasonCode", evaluation.reasonCode());
        result.put("financiallyHonored", true);
        result.put("financialPolicy", "LOCAL_VERIFIED_PENDING_HONORED");
        result.put("receiverEvidenceConfirmed", receiverEvidenceConfirmed);
        result.put("senderAuthorizationCompleted", senderAuthorizationCompleted);
        result.put("validationStartedFromPending", validationStartedFromPending);
        result.put("originalSettlementStatus", evaluation.status().name());
        result.put("financialReleaseAction", "RELEASE");
        return jsonService.write(result);
    }

    private String preserveFinancialHonorReasonCode(
            boolean localCompletedRejectedRetry,
            boolean financiallyHonored,
            String existingReasonCode,
            String evaluatedReasonCode
    ) {
        if (!localCompletedRejectedRetry || !financiallyHonored) {
            return evaluatedReasonCode;
        }
        if (existingReasonCode == null || existingReasonCode.isBlank()) {
            return evaluatedReasonCode;
        }
        return existingReasonCode;
    }

    private boolean hasCompletedSenderAuthorization(OfflinePaymentProof proof) {
        JsonNode payload = jsonService.readTree(proof.rawPayloadJson());
        if (!payload.path("senderAuthRequired").asBoolean(false)) {
            return true;
        }
        if (!payload.path("senderLocalBlock").asBoolean(false)) {
            return false;
        }
        String localSagaStatus = payload.path("localSagaStatus").asText("");
        return SENDER_AUTH_COMPLETED_SAGA_STATUSES.contains(localSagaStatus == null ? "" : localSagaStatus.trim());
    }

    private boolean markVerifiedEvidenceForProof(OfflinePaymentProof proof, boolean receiverEvidenceMatched) {
        localEvidenceRepository.markMatchingEvidence(proof);
        boolean receiverEvidenceConfirmed = receiverEvidenceMatched || localEvidenceRepository.existsMatchingReceiverEvidence(proof);
        if (receiverEvidenceConfirmed) {
            localEvidenceRepository.markMatchingReceiverEvidence(proof);
        }
        return receiverEvidenceConfirmed;
    }

    private int currentAttemptCount(String summaryJson) {
        JsonNode summaryNode = jsonService.readTree(summaryJson);
        return summaryNode.path("attemptCount").asInt(0);
    }

    private SettlementEvaluation evaluateProof(OfflinePaymentProof proof, CollateralLock collateral, String settlementId) {
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
        ProofPayloadConsistencyValidator.RiskAssessment hybridTimeRisk = proofPayloadConsistencyValidator.assessHybridTimeRisk(proof);
        if (hybridTimeRisk.riskDetected()) {
            return conflicted(hybridTimeRisk.reasonCode(), proof, hybridTimeRisk.detailJson());
        }
        if (settlementResultRepository.existsByVoucherIdExcludingSettlementId(proof.voucherId(), settlementId)) {
            return conflicted(
                    OfflinePayReasonCode.DUPLICATE_SETTLEMENT,
                    proof,
                    "{\"reasonCode\":\"" + OfflinePayReasonCode.DUPLICATE_SETTLEMENT + "\"}"
            );
        }

        List<OfflinePaymentProof> existingProofs = findExistingProofChain(collateral, proof).stream()
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

    private List<OfflinePaymentProof> findExistingProofChain(CollateralLock collateral, OfflinePaymentProof proof) {
        List<OfflinePaymentProof> senderAssetProofs = proofRepository.findBySenderDeviceUserAndAsset(
                proof.senderDeviceId(),
                collateral.userId(),
                collateral.assetCode()
        );
        if (senderAssetProofs != null && !senderAssetProofs.isEmpty()) {
            return senderAssetProofs;
        }
        return proofRepository.findByCollateralId(proof.collateralId());
    }

    private CollateralLock resolveSettlementCollateralScope(CollateralLock primaryCollateral, OfflinePaymentProof proof) {
        return collateralRepository.findAggregateByUserIdAndAssetCode(
                        primaryCollateral.userId(),
                        primaryCollateral.assetCode()
                )
                .orElse(primaryCollateral);
    }

    private void deductSettlementAmountAcrossCollateralScope(
            CollateralLock primaryCollateral,
            OfflinePaymentProof proof,
            SettlementRequest request,
            SettlementStatus settlementStatus,
            boolean financiallyHonored
    ) {
        BigDecimal remainingToDeduct = feeCalculator.calculateTotal(primaryCollateral.assetCode(), proof.amount());
        List<CollateralLock> collateralScope = collateralRepository.findActiveByUserIdAndAssetCode(
                primaryCollateral.userId(),
                primaryCollateral.assetCode()
        );
        if (collateralScope == null || collateralScope.isEmpty()) {
            collateralScope = List.of(primaryCollateral);
        }
        for (CollateralLock collateral : collateralScope) {
            if (remainingToDeduct.signum() <= 0) {
                break;
            }
            BigDecimal deduction = collateral.remainingAmount().min(remainingToDeduct);
            if (deduction.signum() <= 0) {
                continue;
            }
            collateralRepository.deductLockedAndRemainingAmount(collateral.id(), deduction);
            collateralRepository.updateStatus(
                    collateral.id(),
                    CollateralStatus.PARTIALLY_SETTLED,
                    jsonService.write(Map.of(
                            "lastSettlementId", request.id(),
                            "lastVoucherId", proof.voucherId(),
                            "lastStatus", settlementStatus.name(),
                            "financiallyHonored", financiallyHonored,
                            "deductedAmount", deduction.toPlainString(),
                            "transferAmount", proof.amount().toPlainString(),
                            "feeAmount", feeCalculator.calculateFee(primaryCollateral.assetCode(), proof.amount()).toPlainString(),
                            "aggregateSettlement", true
                    ))
            );
            remainingToDeduct = remainingToDeduct.subtract(deduction);
        }

        if (remainingToDeduct.signum() > 0) {
            throw new IllegalStateException("aggregate collateral deduction underflow: " + remainingToDeduct.toPlainString());
        }
    }

    private String extractRequestId(OfflinePaymentProof proof) {
        String requestId = jsonService.readTree(proof.rawPayloadJson()).path("requestId").asText(null);
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requestId.trim();
    }

    private String extractRequestId(Map<String, Object> payload) {
        Object requestId = payload.get("requestId");
        if (!(requestId instanceof String value) || value.isBlank()) {
            return null;
        }
        return value.trim();
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
            SettlementEvaluation evaluation,
            boolean financiallyHonored
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

        // receiver history/ledger credit: only when receiver device is registered and settlement is financially honored.
        Device senderDevice = deviceIdentifierResolver.resolve(proof.senderDeviceId()).orElse(null);
        Device receiverDevice = null;
        if (evaluation.status() == SettlementStatus.SETTLED || financiallyHonored) {
            receiverDevice = resolveCurrentReceiverDeviceForProofOwner(proof).orElse(null);
        }
        boolean receiverWalletSettlementRequested = receiverDevice != null
                && "RECEIVER".equalsIgnoreCase(proof.uploaderType())
                && shouldAutoConfirmReceiverSettlement(proof);
        String financialReleaseAction = financiallyHonored ? "RELEASE" : evaluation.releaseAction();
        String reasonCode = request.reasonCode() == null || request.reasonCode().isBlank()
                ? evaluation.reasonCode()
                : request.reasonCode();
        String evaluatedReasonCode = evaluation.reasonCode();

        CoinManageSettlementPort.SettlementLedgerCommand ledgerCommand = settlementSyncCommandFactory.createLedgerCommand(
                collateral,
                proof,
                proof.amount(),
                request,
                evaluation.status().name(),
                financialReleaseAction,
                evaluation.conflictDetected(),
                receiverDevice,
                receiverWalletSettlementRequested,
                financiallyHonored
        );
        FoxCoinHistoryPort.SettlementHistoryCommand historyCommand = settlementSyncCommandFactory.createHistoryCommand(
                collateral,
                proof,
                proof.amount(),
                request,
                evaluation.status().name(),
                financialReleaseAction,
                evaluation.conflictDetected()
        );
        FoxCoinHistoryPort.SettlementHistoryCommand receiverHistoryCommand = null;
        if (
                receiverDevice != null
                        && "RECEIVER".equalsIgnoreCase(proof.uploaderType())
                        && shouldAutoConfirmReceiverSettlement(proof)
        ) {
            receiverHistoryCommand = settlementSyncCommandFactory.createReceiverHistoryCommand(
                    collateral,
                    proof.id(),
                    proof.amount(),
                    request,
                    evaluation.status().name(),
                    financialReleaseAction,
                    receiverDevice
            );
        }

        Map<String, Object> historyCommandMap = Map.ofEntries(
                Map.entry("settlementId", historyCommand.settlementId()),
                Map.entry("transferRef", historyCommand.transferRef()),
                Map.entry("batchId", historyCommand.batchId()),
                Map.entry("collateralId", historyCommand.collateralId()),
                Map.entry("proofId", historyCommand.proofId()),
                Map.entry("userId", historyCommand.userId()),
                Map.entry("deviceId", historyCommand.deviceId()),
                Map.entry("assetCode", historyCommand.assetCode()),
                Map.entry("amount", historyCommand.amount()),
                Map.entry("feeAmount", historyCommand.feeAmount()),
                Map.entry("settlementStatus", historyCommand.settlementStatus()),
                Map.entry("historyType", historyCommand.historyType())
        );

        Map<String, Object> eventPayload = new java.util.LinkedHashMap<>();
        eventPayload.put("settlementId", request.id());
        eventPayload.put("batchId", request.batchId());
        eventPayload.put("proofId", proof.id());
        eventPayload.put("voucherId", proof.voucherId());
        Map<String, Object> senderDeviceSyncCommand = toDeviceSyncCommand(senderDevice);
        if (senderDeviceSyncCommand != null) {
            eventPayload.put("senderDeviceSyncCommand", senderDeviceSyncCommand);
        }
        Map<String, Object> receiverDeviceSyncCommand = toDeviceSyncCommand(receiverDevice);
        if (receiverDeviceSyncCommand != null) {
            eventPayload.put("receiverDeviceSyncCommand", receiverDeviceSyncCommand);
        }
        Map<String, Object> ledgerCommandMap = new java.util.LinkedHashMap<>();
        ledgerCommandMap.put("settlementId", ledgerCommand.settlementId());
        ledgerCommandMap.put("batchId", ledgerCommand.batchId());
        ledgerCommandMap.put("collateralId", ledgerCommand.collateralId());
        ledgerCommandMap.put("proofId", ledgerCommand.proofId());
        ledgerCommandMap.put("userId", ledgerCommand.userId());
        ledgerCommandMap.put("deviceId", ledgerCommand.deviceId());
        if (ledgerCommand.receiverUserId() != null) {
            ledgerCommandMap.put("receiverUserId", ledgerCommand.receiverUserId());
        }
        if (ledgerCommand.receiverDeviceId() != null) {
            ledgerCommandMap.put("receiverDeviceId", ledgerCommand.receiverDeviceId());
        }
        ledgerCommandMap.put("receiverWalletSettlementRequested", ledgerCommand.receiverWalletSettlementRequested());
        ledgerCommandMap.put("assetCode", ledgerCommand.assetCode());
        ledgerCommandMap.put("amount", ledgerCommand.amount());
        ledgerCommandMap.put("feeAmount", ledgerCommand.feeAmount());
        ledgerCommandMap.put("settlementStatus", ledgerCommand.settlementStatus());
        ledgerCommandMap.put("releaseAction", ledgerCommand.releaseAction());
        ledgerCommandMap.put("conflictDetected", ledgerCommand.conflictDetected());
        ledgerCommandMap.put("financiallyHonored", ledgerCommand.financiallyHonored());
        if (reasonCode != null && !reasonCode.isBlank()) {
            ledgerCommandMap.put("reasonCode", reasonCode);
        }
        if (evaluatedReasonCode != null && !evaluatedReasonCode.isBlank()) {
            ledgerCommandMap.put("evaluatedReasonCode", evaluatedReasonCode);
        }
        ledgerCommandMap.put("proofFingerprint", ledgerCommand.proofFingerprint());
        ledgerCommandMap.put("newStateHash", ledgerCommand.newStateHash());
        ledgerCommandMap.put("previousHash", ledgerCommand.previousHash());
        ledgerCommandMap.put("monotonicCounter", ledgerCommand.monotonicCounter());
        ledgerCommandMap.put("nonce", ledgerCommand.nonce());
        ledgerCommandMap.put("signature", ledgerCommand.signature());
        eventPayload.put("ledgerCommand", ledgerCommandMap);
        eventPayload.put("historyCommand", historyCommandMap);
        if (reasonCode != null && !reasonCode.isBlank()) {
            eventPayload.put("reasonCode", reasonCode);
        }
        if (evaluatedReasonCode != null && !evaluatedReasonCode.isBlank()) {
            eventPayload.put("evaluatedReasonCode", evaluatedReasonCode);
        }
        if (receiverHistoryCommand != null) {
            eventPayload.put("receiverHistoryCommand", Map.ofEntries(
                    Map.entry("settlementId", receiverHistoryCommand.settlementId()),
                    Map.entry("transferRef", receiverHistoryCommand.transferRef()),
                    Map.entry("batchId", receiverHistoryCommand.batchId()),
                    Map.entry("collateralId", receiverHistoryCommand.collateralId()),
                    Map.entry("proofId", receiverHistoryCommand.proofId()),
                    Map.entry("userId", receiverHistoryCommand.userId()),
                    Map.entry("deviceId", receiverHistoryCommand.deviceId()),
                    Map.entry("assetCode", receiverHistoryCommand.assetCode()),
                    Map.entry("amount", receiverHistoryCommand.amount()),
                    Map.entry("feeAmount", receiverHistoryCommand.feeAmount()),
                    Map.entry("settlementStatus", receiverHistoryCommand.settlementStatus()),
                    Map.entry("historyType", receiverHistoryCommand.historyType())
            ));
        }
        eventPayload.put("requestedAt", OffsetDateTime.now().toString());

        eventBus.publishExternalSyncRequested(
                "LEDGER_SYNC_REQUESTED",
                request.id(),
                request.batchId(),
                proof.id(),
                jsonService.write(eventPayload),
                OffsetDateTime.now().toString()
        );
    }

    private Map<String, Object> toDeviceSyncCommand(Device device) {
        if (device == null) {
            return null;
        }
        return Map.of(
                "userId", device.userId(),
                "deviceId", device.deviceId(),
                "status", toCoinManageDeviceStatus(device.status()),
                "keyVersion", device.keyVersion()
        );
    }

    private String toCoinManageDeviceStatus(DeviceStatus status) {
        return status == DeviceStatus.ACTIVE ? "ACTIVE" : "REVOKED";
    }

    private void saveReconciliationCase(
            SettlementRequest request,
            OfflinePaymentProof proof,
            SettlementEvaluation evaluation,
            String resultJson
    ) {
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
                resultJson
        );
    }

    private void scheduleFailedSettlementRelease(
            CollateralLock collateral,
            OfflinePaymentProof proof,
            SettlementRequest request,
            String reasonCode
    ) {
        String referenceId = "release:" + request.id() + ":failed_settlement";
        var operation = collateralOperationRepository.saveRequested(
                collateral.id(),
                collateral.userId(),
                proof.senderDeviceId(),
                collateral.assetCode(),
                io.korion.offlinepay.domain.status.CollateralOperationType.RELEASE,
                proof.amount(),
                referenceId,
                jsonService.write(Map.of(
                        "amount", proof.amount(),
                        "reason", "failed_settlement_release",
                        "settlementId", request.id(),
                        "proofId", proof.id(),
                        "reasonCode", reasonCode
                ))
        );
        eventBus.publishCollateralOperationRequested(
                operation.id(),
                operation.operationType().name(),
                operation.assetCode(),
                operation.referenceId(),
                operation.createdAt().toString()
        );
        offlineSagaService.start(
                OfflineSagaType.COLLATERAL_RELEASE,
                operation.id(),
                "SERVER_ACCEPTED",
                Map.of(
                        "operationId", operation.id(),
                        "assetCode", operation.assetCode(),
                        "referenceId", operation.referenceId(),
                        "amount", operation.amount().toPlainString(),
                        "settlementId", request.id(),
                        "proofId", proof.id(),
                        "reasonCode", reasonCode
                )
        );
    }

    private SettlementBatchStatus resolveBatchStatus(int settledCount, int failedCount, int pendingCount, boolean hasConflict) {
        if (pendingCount > 0 && settledCount == 0 && failedCount == 0) {
            return SettlementBatchStatus.UPLOADED;
        }
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
        if (OfflinePayReasonCode.LOCAL_AVAILABLE_AMOUNT_EXCEEDED.equals(reasonCode)
                || OfflinePayReasonCode.SERVER_AVAILABLE_AMOUNT_EXCEEDED.equals(reasonCode)
                || OfflinePayReasonCode.INSUFFICIENT_REMAINING_AMOUNT.equals(reasonCode)) {
            return "OVERSPEND_ATTEMPT";
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
        return feeCalculator.calculateTotal(collateral.assetCode(), proof.amount()).compareTo(collateral.remainingAmount()) >= 0
                ? CollateralStatus.RELEASED
                : CollateralStatus.PARTIALLY_SETTLED;
    }

    public record SubmitSettlementBatchCommand(
            UploaderType uploaderType,
            String uploaderDeviceId,
            String idempotencyKey,
            List<ProofSubmission> proofs,
            String triggerMode
    ) {}

    public record LocalEvidenceIngestCommand(
            UploaderType uploaderType,
            String uploaderDeviceId,
            String idempotencyKey,
            List<LocalEvidenceSubmission> evidences
    ) {}

    public record ConfirmReceivedSettlementsCommand(
            long userId,
            List<String> proofIds
    ) {}

    public record ReceiverSettlementConfirmationResult(
            int requested,
            int confirmed,
            int skipped
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

    public record LocalEvidenceSubmission(
            String voucherId,
            String sessionId,
            String direction,
            String senderDeviceId,
            String receiverDeviceId,
            BigDecimal amount,
            Long counter,
            String previousHash,
            String hashChainHead,
            String nonce,
            String signature,
            String canonicalPayload,
            String merchantId,
            String partnerId,
            String leaderId,
            String countryCode,
            String storeId,
            String orderId,
            String paymentIntentId,
            String invoiceId,
            String fiatAmount,
            String fiatCurrency,
            String exchangeRate,
            String rateTimestamp,
            String schemaVersion,
            String protocolVersion,
            String hashAlgorithm,
            String signatureAlgorithm,
            String keyId,
            String publicKeyFingerprint,
            String appVersion,
            String deviceAttestationId,
            String deviceAttestationVerdict,
            String serverVerifiedTrustLevel,
            String serverAttestationVerifiedAt,
            String transportSessionHash,
            String transportTranscriptSource,
            String transportTranscript,
            String transportTranscriptEncoding,
            Map<String, Object> payload
    ) {
        public LocalEvidenceSubmission(
                String voucherId,
                String sessionId,
                String direction,
                String senderDeviceId,
                String receiverDeviceId,
                BigDecimal amount,
                Long counter,
                String previousHash,
                String hashChainHead,
                String nonce,
                String signature,
                String canonicalPayload,
                Map<String, Object> payload
        ) {
            this(
                    voucherId,
                    sessionId,
                    direction,
                    senderDeviceId,
                    receiverDeviceId,
                    amount,
                    counter,
                    previousHash,
                    hashChainHead,
                    nonce,
                    signature,
                    canonicalPayload,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    payload
            );
        }
    }

    public record LocalEvidenceIngestResult(
            int requested,
            int stored,
            int skipped,
            int matched,
            int awaitingCarrier
    ) {}

    public record DirectLocalEvidenceReconcileResult(
            int candidates,
            int created,
            int reused,
            int finalized,
            int rejected,
            int skipped,
            List<String> batchIds,
            List<String> settlementIds
    ) {}

    public record LocalEvidenceStatusResult(
            String voucherId,
            String sessionId,
            int total,
            int stored,
            int matched,
            int awaitingCarrier,
            int failed,
            int senderStored,
            int receiverStored,
            int senderMatched,
            int receiverMatched,
            int senderFailed,
            int receiverFailed,
            String state,
            int staleAwaitingCarrier,
            int staleAfterHours,
            String oldestAwaitingCarrierAt,
            String latestUpdatedAt
    ) {}

    private record LocalEvidenceVerification(
            String status,
            String detail
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

    public record SettlementDetailView(
            SettlementRequest settlementRequest,
            OfflineSaga settlementSaga,
            ReconciliationCase reconciliationCase,
            OfflinePaymentProof proof,
            CollateralLock collateral
    ) {}

    public record SettlementBatchDetailView(
            SettlementBatch batch,
            List<SettlementDetailView> settlements
    ) {}
}
