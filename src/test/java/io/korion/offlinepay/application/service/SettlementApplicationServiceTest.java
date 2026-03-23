package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.korion.offlinepay.application.service.settlement.ProofChainValidator;
import io.korion.offlinepay.application.service.settlement.ProofConflictDetector;
import io.korion.offlinepay.application.service.settlement.ProofPayloadConsistencyValidator;
import io.korion.offlinepay.application.service.settlement.ProofSchemaValidator;
import io.korion.offlinepay.application.service.settlement.ProofFingerprintService;
import io.korion.offlinepay.application.service.settlement.SpendingProofHashService;
import io.korion.offlinepay.application.service.settlement.SettlementPolicyEvaluator;
import io.korion.offlinepay.application.service.settlement.DeviceSignatureVerificationService;
import io.korion.offlinepay.application.service.settlement.DeviceBindingVerificationService;
import io.korion.offlinepay.application.service.settlement.IssuedProofVerificationService;
import io.korion.offlinepay.domain.model.IssuedOfflineProof;
import io.korion.offlinepay.domain.status.IssuedProofStatus;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SettlementApplicationServiceTest {

    private final SpendingProofHashService spendingProofHashService = new SpendingProofHashService();

    private final CollateralRepository collateralRepository = Mockito.mock(CollateralRepository.class);
    private final DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
    private final OfflinePaymentProofRepository proofRepository = Mockito.mock(OfflinePaymentProofRepository.class);
    private final SettlementBatchRepository batchRepository = Mockito.mock(SettlementBatchRepository.class);
    private final SettlementRepository settlementRepository = Mockito.mock(SettlementRepository.class);
    private final SettlementResultRepository settlementResultRepository = Mockito.mock(SettlementResultRepository.class);
    private final ReconciliationCaseRepository reconciliationCaseRepository = Mockito.mock(ReconciliationCaseRepository.class);
    private final SettlementConflictRepository settlementConflictRepository = Mockito.mock(SettlementConflictRepository.class);
    private final SettlementBatchEventBus eventBus = Mockito.mock(SettlementBatchEventBus.class);
    private final CoinManageSettlementPort coinManageSettlementPort = Mockito.mock(CoinManageSettlementPort.class);
    private final FoxCoinHistoryPort foxCoinHistoryPort = Mockito.mock(FoxCoinHistoryPort.class);
    private final IssuedProofVerificationService issuedProofVerificationService = Mockito.mock(IssuedProofVerificationService.class);
    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final SettlementApplicationService service = new SettlementApplicationService(
            collateralRepository,
            deviceRepository,
            proofRepository,
            batchRepository,
            settlementRepository,
            settlementResultRepository,
            reconciliationCaseRepository,
            settlementConflictRepository,
            eventBus,
            coinManageSettlementPort,
            foxCoinHistoryPort,
            jsonService,
            new SettlementBatchFactory(jsonService),
            new SettlementRequestFactory(jsonService),
            new SettlementStreamEventFactory(),
            new SettlementSyncCommandFactory(new ProofFingerprintService()),
            new ProofSchemaValidator(),
            new ProofPayloadConsistencyValidator(jsonService),
            new ProofConflictDetector(jsonService),
            new ProofChainValidator(jsonService, spendingProofHashService),
            new SettlementPolicyEvaluator(jsonService),
            new DeviceSignatureVerificationService(),
            new DeviceBindingVerificationService(jsonService),
            issuedProofVerificationService
    );

    {
        when(issuedProofVerificationService.verify(any())).thenReturn(
                IssuedProofVerificationService.VerificationResult.valid(
                        new IssuedOfflineProof(
                                "issued-proof-1",
                                77L,
                                "device-1",
                                "collateral-1",
                                "USDT",
                                new BigDecimal("1000"),
                                "proof_nonce",
                                "issuer-key",
                                "issuer-public-key",
                                "issuer-signature",
                                "{}",
                                IssuedProofStatus.ACTIVE,
                                null,
                                OffsetDateTime.now().plusHours(1),
                                OffsetDateTime.now(),
                                OffsetDateTime.now()
                        )
                )
        );
    }

    @Test
    void finalizeSettlementSyncsCoinManageLedgerAndFoxCoinHistory() {
        SettlementRequest request = new SettlementRequest(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-1",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-1",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementRequest settled = new SettlementRequest(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                SettlementStatus.SETTLED,
                null,
                false,
                "{\"releaseAction\":\"RELEASE\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String proofHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("100"),
                1L,
                "device-1",
                "nonce-1"
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-1",
                "batch-1",
                "voucher-1",
                "collateral-1",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-1",
                proofHash,
                "GENESIS",
                "signature",
                new BigDecimal("100"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{\"voucherId\":\"voucher-1\"}",
                "SENDER",
                "{\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false}",
                OffsetDateTime.now()
        );
        Device device = new Device(
                "row-1",
                "device-1",
                77L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(settlementRepository.findById("settlement-1"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(settled));
        when(collateralRepository.findById("collateral-1")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-1")).thenReturn(Optional.of(proof));
        when(proofRepository.findByCollateralId("collateral-1")).thenReturn(java.util.List.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-1")).thenReturn(false);

        SettlementRequest result = service.finalizeSettlement("settlement-1");

        verify(coinManageSettlementPort).finalizeSettlement(any(CoinManageSettlementPort.SettlementLedgerCommand.class));
        verify(foxCoinHistoryPort).recordSettlementHistory(any(FoxCoinHistoryPort.SettlementHistoryCommand.class));
        verify(settlementRepository).update(anyString(), any(SettlementStatus.class), any(), anyBoolean(), anyString());
        assertEquals(SettlementStatus.SETTLED, result.status());
    }

    @Test
    void finalizeSettlementRejectsInvalidDeviceBinding() {
        SettlementRequest request = new SettlementRequest(
                "settlement-2",
                "batch-2",
                "collateral-2",
                "proof-2",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-2",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-2",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-2",
                "batch-2",
                "voucher-2",
                "collateral-2",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-2",
                spendingProofHashService.computeNewStateHash("GENESIS", new BigDecimal("10"), 1L, "device-1", "nonce-2"),
                "GENESIS",
                "local_sig_fake",
                new BigDecimal("10"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"deviceRegistrationId\":\"other-row\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false}",
                OffsetDateTime.now()
        );
        Device device = new Device(
                "row-1",
                "device-1",
                77L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(settlementRepository.findById("settlement-2"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-2",
                        "batch-2",
                        "collateral-2",
                        "proof-2",
                        SettlementStatus.REJECTED,
                        "INVALID_DEVICE_BINDING",
                        false,
                        "{\"reasonCode\":\"INVALID_DEVICE_BINDING\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-2")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-2")).thenReturn(Optional.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));

        SettlementRequest result = service.finalizeSettlement("settlement-2");

        verify(settlementRepository).update(anyString(), any(SettlementStatus.class), any(), anyBoolean(), anyString());
        assertEquals(SettlementStatus.REJECTED, result.status());
        assertTrue(result.settlementResultJson().contains("INVALID_DEVICE_BINDING"));
    }

    @Test
    void finalizeSettlementRejectsDualAmountConflict() {
        SettlementRequest request = new SettlementRequest(
                "settlement-3",
                "batch-3",
                "collateral-3",
                "proof-3",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-3",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-3",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-3",
                "batch-3",
                "voucher-3",
                "collateral-3",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-3",
                spendingProofHashService.computeNewStateHash("GENESIS", new BigDecimal("10"), 1L, "device-1", "nonce-3"),
                "GENESIS",
                "signature",
                new BigDecimal("10"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":true}",
                OffsetDateTime.now()
        );
        Device device = new Device(
                "row-1",
                "device-1",
                77L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(settlementRepository.findById("settlement-3"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-3",
                        "batch-3",
                        "collateral-3",
                        "proof-3",
                        SettlementStatus.REJECTED,
                        "AMOUNT_CONFLICT_DETECTED",
                        false,
                        "{\"reasonCode\":\"AMOUNT_CONFLICT_DETECTED\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-3")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-3")).thenReturn(Optional.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));

        SettlementRequest result = service.finalizeSettlement("settlement-3");

        verify(settlementRepository).update(anyString(), any(SettlementStatus.class), any(), anyBoolean(), anyString());
        assertEquals(SettlementStatus.REJECTED, result.status());
        assertTrue(result.settlementResultJson().contains("AMOUNT_CONFLICT_DETECTED"));
    }

    @Test
    void finalizeSettlementRejectsInvalidLedgerExecutionMode() {
        SettlementRequest request = new SettlementRequest(
                "settlement-4",
                "batch-4",
                "collateral-4",
                "proof-4",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-4",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-4",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-4",
                "batch-4",
                "voucher-4",
                "collateral-4",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-4",
                spendingProofHashService.computeNewStateHash("GENESIS", new BigDecimal("10"), 1L, "device-1", "nonce-4"),
                "GENESIS",
                "signature",
                new BigDecimal("10"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"AUTO_WITHDRAW\",\"dualAmountEntered\":false}",
                OffsetDateTime.now()
        );
        Device device = new Device(
                "row-1",
                "device-1",
                77L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(settlementRepository.findById("settlement-4"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-4",
                        "batch-4",
                        "collateral-4",
                        "proof-4",
                        SettlementStatus.REJECTED,
                        "LEDGER_EXECUTION_MODE_INVALID",
                        false,
                        "{\"reasonCode\":\"LEDGER_EXECUTION_MODE_INVALID\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-4")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-4")).thenReturn(Optional.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));

        SettlementRequest result = service.finalizeSettlement("settlement-4");

        verify(settlementRepository).update(anyString(), any(SettlementStatus.class), any(), anyBoolean(), anyString());
        assertEquals(SettlementStatus.REJECTED, result.status());
        assertTrue(result.settlementResultJson().contains("LEDGER_EXECUTION_MODE_INVALID"));
    }

    @Test
    void finalizeSettlementRejectsMissingPaymentModeInsteadOfThrowing() {
        SettlementRequest request = new SettlementRequest(
                "settlement-5",
                "batch-5",
                "collateral-5",
                "proof-5",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-5",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-5",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-5",
                "batch-5",
                "voucher-5",
                "collateral-5",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-5",
                spendingProofHashService.computeNewStateHash("GENESIS", new BigDecimal("10"), 1L, "device-1", "nonce-5"),
                "GENESIS",
                "signature",
                new BigDecimal("10"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false}",
                OffsetDateTime.now()
        );
        Device device = new Device(
                "row-1",
                "device-1",
                77L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(settlementRepository.findById("settlement-5"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-5",
                        "batch-5",
                        "collateral-5",
                        "proof-5",
                        SettlementStatus.REJECTED,
                        "PAYMENT_MODE_REQUIRED",
                        false,
                        "{\"reasonCode\":\"PAYMENT_MODE_REQUIRED\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-5")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-5")).thenReturn(Optional.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));

        SettlementRequest result = service.finalizeSettlement("settlement-5");

        verify(settlementRepository).update(anyString(), any(SettlementStatus.class), any(), anyBoolean(), anyString());
        assertEquals(SettlementStatus.REJECTED, result.status());
        assertTrue(result.settlementResultJson().contains("PAYMENT_MODE_REQUIRED"));
    }

    @Test
    void finalizeSettlementRejectsPayloadAmountMismatch() {
        SettlementRequest request = new SettlementRequest(
                "settlement-6",
                "batch-6",
                "collateral-6",
                "proof-6",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-6",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-6",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        long now = System.currentTimeMillis();
        String computedHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("10"),
                1L,
                "device-1",
                "nonce-6"
        );
        String rawPayloadJson = "{\"voucherId\":\"voucher-6\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"9.99\",\"expiresAt\":\"9999999999999\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"9.99\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-6\",\"newStateHash\":\""
                + computedHash
                + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"signature\",\"timestamp\":\""
                + now
                + "\"}}";
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-6",
                "batch-6",
                "voucher-6",
                "collateral-6",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-6",
                computedHash,
                "GENESIS",
                "signature",
                new BigDecimal("10"),
                now,
                now + 60_000,
                "{\"voucherId\":\"voucher-6\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                rawPayloadJson,
                OffsetDateTime.now()
        );

        when(settlementRepository.findById("settlement-6"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-6",
                        "batch-6",
                        "collateral-6",
                        "proof-6",
                        SettlementStatus.REJECTED,
                        "PAYLOAD_AMOUNT_MISMATCH",
                        false,
                        "{\"reasonCode\":\"PAYLOAD_AMOUNT_MISMATCH\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-6")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-6")).thenReturn(Optional.of(proof));

        SettlementRequest result = service.finalizeSettlement("settlement-6");

        assertEquals(SettlementStatus.REJECTED, result.status());
        assertTrue(result.settlementResultJson().contains("PAYLOAD_AMOUNT_MISMATCH"));
    }

    @Test
    void recordBatchProcessingFailureCreatesReconciliationCaseWhenDeadLettered() {
        SettlementBatch batch = new SettlementBatch(
                "batch-dead-1",
                "device-1",
                "idem-1",
                SettlementBatchStatus.UPLOADED,
                null,
                1,
                "{\"attemptCount\":1}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(batchRepository.findById("batch-dead-1")).thenReturn(Optional.of(batch));

        SettlementApplicationService.BatchFailureOutcome outcome =
                service.recordBatchProcessingFailure("batch-dead-1", "sync timeout", 2);

        assertTrue(outcome.deadLettered());
        verify(reconciliationCaseRepository).save(
                eq(null),
                eq("batch-dead-1"),
                eq(null),
                eq(null),
                eq("BATCH_SYNC_FAILED"),
                eq(io.korion.offlinepay.domain.status.ReconciliationCaseStatus.OPEN),
                eq("BATCH_SYNC_FAIL"),
                anyString()
        );
    }
}
