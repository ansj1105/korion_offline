package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.port.SettlementConflictRepository;
import io.korion.offlinepay.application.port.SettlementRepository;
import io.korion.offlinepay.application.service.settlement.ConflictDetectionResult;
import io.korion.offlinepay.application.service.settlement.ProofConflictDetector;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PostFinalProofConflictScanWorker {

    private static final String CASE_TYPE = "POST_FINAL_PROOF_CONFLICT";

    private final OfflinePaymentProofRepository proofRepository;
    private final CollateralRepository collateralRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementConflictRepository settlementConflictRepository;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final SettlementBatchEventBus eventBus;
    private final ProofConflictDetector proofConflictDetector;
    private final TelegramAlertService telegramAlertService;
    private final JsonService jsonService;
    private final AppProperties properties;

    public PostFinalProofConflictScanWorker(
            OfflinePaymentProofRepository proofRepository,
            CollateralRepository collateralRepository,
            SettlementRepository settlementRepository,
            SettlementConflictRepository settlementConflictRepository,
            ReconciliationCaseRepository reconciliationCaseRepository,
            SettlementBatchEventBus eventBus,
            ProofConflictDetector proofConflictDetector,
            TelegramAlertService telegramAlertService,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.proofRepository = proofRepository;
        this.collateralRepository = collateralRepository;
        this.settlementRepository = settlementRepository;
        this.settlementConflictRepository = settlementConflictRepository;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.eventBus = eventBus;
        this.proofConflictDetector = proofConflictDetector;
        this.telegramAlertService = telegramAlertService;
        this.jsonService = jsonService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.post-final-conflict-scan-delay-ms:60000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }
        for (OfflinePaymentProof candidate : proofRepository.findPostFinalConflictScanCandidates(properties.settlementStreamBatchSize())) {
            scan(candidate);
        }
    }

    private void scan(OfflinePaymentProof candidate) {
        if (candidate == null || candidate.status() != OfflineProofStatus.SETTLED) {
            return;
        }
        if (reconciliationCaseRepository.findOpenByVoucherIdAndCaseType(candidate.voucherId(), CASE_TYPE).isPresent()) {
            proofRepository.markPostFinalConflictScanned(candidate.id());
            return;
        }
        CollateralLock collateral = collateralRepository.findById(candidate.collateralId()).orElse(null);
        if (collateral == null) {
            return;
        }
        List<OfflinePaymentProof> existingSettledProofs = proofRepository
                .findBySenderDeviceUserAndAsset(candidate.senderDeviceId(), collateral.userId(), collateral.assetCode())
                .stream()
                .filter(existing -> existing != null
                        && !existing.id().equals(candidate.id())
                        && existing.status() == OfflineProofStatus.SETTLED)
                .toList();
        ConflictDetectionResult conflict = proofConflictDetector.detect(existingSettledProofs, candidate);
        if (!conflict.conflicted()) {
            proofRepository.markPostFinalConflictScanned(candidate.id());
            return;
        }
        SettlementRequest settlement = settlementRepository.findLatestByProofId(candidate.id()).orElse(null);
        if (settlement == null) {
            return;
        }
        String detailJson = jsonService.write(Map.ofEntries(
                Map.entry("reasonCode", conflict.conflictType()),
                Map.entry("conflictDetail", jsonService.readTree(conflict.detailJson())),
                Map.entry("candidateProofId", candidate.id()),
                Map.entry("candidateVoucherId", candidate.voucherId()),
                Map.entry("candidateSettlementId", settlement.id()),
                Map.entry("collateralId", candidate.collateralId()),
                Map.entry("senderDeviceId", candidate.senderDeviceId()),
                Map.entry("assetCode", collateral.assetCode()),
                Map.entry("retryable", false),
                Map.entry("adminAction", "REVIEW_POST_FINAL_CONFLICT_AND_COMPENSATE_IF_REQUIRED"),
                Map.entry("detectedAt", OffsetDateTime.now().toString())
        ));
        settlementConflictRepository.save(
                settlement.id(),
                candidate.voucherId(),
                candidate.collateralId(),
                candidate.senderDeviceId(),
                conflict.conflictType(),
                "CRITICAL",
                detailJson
        );
        reconciliationCaseRepository.save(
                settlement.id(),
                settlement.batchId(),
                candidate.id(),
                candidate.voucherId(),
                CASE_TYPE,
                ReconciliationCaseStatus.OPEN,
                conflict.conflictType() == null ? OfflinePayReasonCode.UNKNOWN_CONFLICT : conflict.conflictType(),
                detailJson
        );
        eventBus.publishConflict(
                settlement.batchId(),
                candidate.voucherId(),
                candidate.collateralId(),
                conflict.conflictType(),
                "CRITICAL",
                OffsetDateTime.now().toString()
        );
        telegramAlertService.notifyOperationalIssue(
                "offline_pay.post_final_proof_conflict",
                "settlementId=" + settlement.id()
                        + ", proofId=" + candidate.id()
                        + ", voucherId=" + candidate.voucherId()
                        + ", conflictType=" + conflict.conflictType()
        );
        proofRepository.markPostFinalConflictScanned(candidate.id());
    }
}
