package io.korion.offlinepay.application.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.port.SettlementConflictRepository;
import io.korion.offlinepay.application.port.SettlementRepository;
import io.korion.offlinepay.application.service.settlement.ProofConflictDetector;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PostFinalProofConflictScanWorkerTest {

    private final OfflinePaymentProofRepository proofRepository = mock(OfflinePaymentProofRepository.class);
    private final CollateralRepository collateralRepository = mock(CollateralRepository.class);
    private final SettlementRepository settlementRepository = mock(SettlementRepository.class);
    private final SettlementConflictRepository settlementConflictRepository = mock(SettlementConflictRepository.class);
    private final ReconciliationCaseRepository reconciliationCaseRepository = mock(ReconciliationCaseRepository.class);
    private final SettlementBatchEventBus eventBus = mock(SettlementBatchEventBus.class);
    private final TelegramAlertService telegramAlertService = mock(TelegramAlertService.class);
    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final PostFinalProofConflictScanWorker worker = new PostFinalProofConflictScanWorker(
            proofRepository,
            collateralRepository,
            settlementRepository,
            settlementConflictRepository,
            reconciliationCaseRepository,
            eventBus,
            new ProofConflictDetector(jsonService),
            telegramAlertService,
            jsonService,
            properties()
    );

    @Test
    void settledProofConflictCreatesReconciliationCaseConflictEventAndAlert() {
        OfflinePaymentProof existing = proof(
                "00000000-0000-0000-0000-000000000001",
                "voucher-existing",
                7,
                "hash-existing",
                "nonce-1"
        );
        OfflinePaymentProof candidate = proof(
                "00000000-0000-0000-0000-000000000002",
                "voucher-candidate",
                7,
                "hash-candidate",
                "nonce-2"
        );
        CollateralLock collateral = new CollateralLock(
                "00000000-0000-0000-0000-000000000011",
                1L,
                "sender-device",
                "KORI",
                new BigDecimal("100.000000"),
                new BigDecimal("67.000000"),
                "GENESIS",
                1,
                CollateralStatus.PARTIALLY_SETTLED,
                null,
                null,
                "{}",
                OffsetDateTime.parse("2026-06-08T00:00:00Z"),
                OffsetDateTime.parse("2026-06-08T00:10:00Z")
        );
        SettlementRequest settlement = new SettlementRequest(
                "00000000-0000-0000-0000-000000000021",
                "00000000-0000-0000-0000-000000000031",
                candidate.collateralId(),
                candidate.id(),
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                OffsetDateTime.parse("2026-06-08T00:00:00Z"),
                OffsetDateTime.parse("2026-06-08T00:10:00Z")
        );

        when(proofRepository.findPostFinalConflictScanCandidates(20)).thenReturn(List.of(candidate));
        when(reconciliationCaseRepository.findOpenByVoucherIdAndCaseType("voucher-candidate", "POST_FINAL_PROOF_CONFLICT"))
                .thenReturn(Optional.empty());
        when(collateralRepository.findById(candidate.collateralId())).thenReturn(Optional.of(collateral));
        when(proofRepository.findBySenderDeviceUserAndAsset("sender-device", 1L, "KORI"))
                .thenReturn(List.of(existing, candidate));
        when(settlementRepository.findLatestByProofId(candidate.id())).thenReturn(Optional.of(settlement));

        worker.poll();

        String candidateId = candidate.id();
        String candidateVoucherId = candidate.voucherId();
        String candidateCollateralId = candidate.collateralId();
        String candidateSenderDeviceId = candidate.senderDeviceId();
        verify(settlementConflictRepository).save(
                eq(settlement.id()),
                eq(candidateVoucherId),
                eq(candidateCollateralId),
                eq(candidateSenderDeviceId),
                eq(OfflinePayReasonCode.DUPLICATE_COUNTER),
                eq("CRITICAL"),
                anyString()
        );
        verify(reconciliationCaseRepository).save(
                eq(settlement.id()),
                eq(settlement.batchId()),
                eq(candidateId),
                eq(candidateVoucherId),
                eq("POST_FINAL_PROOF_CONFLICT"),
                eq(ReconciliationCaseStatus.OPEN),
                eq(OfflinePayReasonCode.DUPLICATE_COUNTER),
                anyString()
        );
        verify(eventBus).publishConflict(
                eq(settlement.batchId()),
                eq(candidateVoucherId),
                eq(candidateCollateralId),
                eq(OfflinePayReasonCode.DUPLICATE_COUNTER),
                eq("CRITICAL"),
                anyString()
        );
        verify(telegramAlertService).notifyOperationalIssue(
                eq("offline_pay.post_final_proof_conflict"),
                anyString()
        );
        verify(proofRepository).markPostFinalConflictScanned(candidateId);
    }

    @Test
    void existingOpenCasePreventsDuplicateConflictOutput() {
        OfflinePaymentProof candidate = proof(
                "00000000-0000-0000-0000-000000000002",
                "voucher-candidate",
                7,
                "hash-candidate",
                "nonce-2"
        );

        when(proofRepository.findPostFinalConflictScanCandidates(20)).thenReturn(List.of(candidate));
        when(reconciliationCaseRepository.findOpenByVoucherIdAndCaseType("voucher-candidate", "POST_FINAL_PROOF_CONFLICT"))
                .thenReturn(Optional.of(mock(io.korion.offlinepay.domain.model.ReconciliationCase.class)));

        worker.poll();

        verify(settlementConflictRepository, never()).save(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(eventBus, never()).publishConflict(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(proofRepository).markPostFinalConflictScanned(candidate.id());
    }

    private OfflinePaymentProof proof(
            String id,
            String voucherId,
            long counter,
            String hashChainHead,
            String nonce
    ) {
        OfflinePaymentProof proof = mock(OfflinePaymentProof.class);
        when(proof.id()).thenReturn(id);
        when(proof.batchId()).thenReturn("00000000-0000-0000-0000-000000000031");
        when(proof.voucherId()).thenReturn(voucherId);
        when(proof.collateralId()).thenReturn("00000000-0000-0000-0000-000000000011");
        when(proof.senderDeviceId()).thenReturn("sender-device");
        when(proof.status()).thenReturn(OfflineProofStatus.SETTLED);
        when(proof.counter()).thenReturn(counter);
        when(proof.hashChainHead()).thenReturn(hashChainHead);
        when(proof.nonce()).thenReturn(nonce);
        return proof;
    }

    private AppProperties properties() {
        return new AppProperties(
                "KORI",
                24,
                20,
                1000,
                new AppProperties.ProofIssuer("test-proof-issuer", "", ""),
                new AppProperties.CoinManage("http://localhost:3000", "test-key", 5000),
                new AppProperties.FoxCoin("http://localhost:3101", "test-key", 5000),
                new AppProperties.Alerts(
                        new AppProperties.Telegram("", ""),
                        new AppProperties.CircuitBreaker(3, 60000)
                ),
                new AppProperties.Redis(
                        "offlinepay",
                        "stream:settlement:requested",
                        "stream:settlement:result",
                        "stream:settlement:conflict",
                        "stream:settlement:dead-letter",
                        "stream:collateral:requested",
                        "stream:collateral:result",
                        "offlinepay:settlement-group"
                ),
                new AppProperties.Worker(true, "worker-1", 60000, 3)
        );
    }
}
