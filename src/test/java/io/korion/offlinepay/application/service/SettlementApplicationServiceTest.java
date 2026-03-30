package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
    private final OfflineSagaService offlineSagaService = Mockito.mock(OfflineSagaService.class);
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
            offlineSagaService,
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

        verify(eventBus).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                eq("settlement-1"),
                eq("batch-1"),
                eq("proof-1"),
                anyString(),
                anyString()
        );
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
        long now = System.currentTimeMillis();
        long expiresAt = now + 60_000;
        String hash = spendingProofHashService.computeNewStateHash("GENESIS", new BigDecimal("10"), 1L, "device-1", "nonce-2");
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
                hash,
                "GENESIS",
                "local_sig_fake",
                new BigDecimal("10"),
                now,
                expiresAt,
                "{\"voucherId\":\"voucher-2\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"10\",\"expiresAt\":\"9999999999999\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"10\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-2\",\"newStateHash\":\""
                        + hash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_fake\",\"timestamp\":\""
                        + now
                        + "\"}}",
                "SENDER",
                "{\"voucherId\":\"voucher-2\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"10\",\"expiresAt\":\""
                        + expiresAt
                        + "\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"10\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-2\",\"newStateHash\":\""
                        + hash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_fake\",\"timestamp\":\""
                        + now
                        + "\"},\"deviceRegistrationId\":\"other-row\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false}",
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
        verify(reconciliationCaseRepository).save(
                eq("settlement-2"),
                eq("batch-2"),
                eq("proof-2"),
                eq("voucher-2"),
                eq("DEVICE_INVALID"),
                eq(io.korion.offlinepay.domain.status.ReconciliationCaseStatus.OPEN),
                eq("INVALID_DEVICE_BINDING"),
                anyString()
        );
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
    void finalizeSettlementRejectsReplayedRequestId() {
        SettlementRequest request = new SettlementRequest(
                "settlement-request-replay",
                "batch-request-replay",
                "collateral-request-replay",
                "proof-request-replay",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-request-replay",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-request-replay",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        long now = System.currentTimeMillis();
        long expiresAt = now + 60_000;
        String hash = spendingProofHashService.computeNewStateHash("GENESIS", new BigDecimal("10"), 1L, "device-1", "nonce-request-replay");
        String canonicalPayload = "{\"voucherId\":\"voucher-request-replay\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"10\",\"expiresAt\":\""
                + expiresAt
                + "\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"10\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-request-replay\",\"newStateHash\":\""
                + hash
                + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_fake\",\"timestamp\":\""
                + now
                + "\"}}";
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-request-replay",
                "batch-request-replay",
                "voucher-request-replay",
                "collateral-request-replay",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-request-replay",
                hash,
                "GENESIS",
                "local_sig_fake",
                new BigDecimal("10"),
                now,
                expiresAt,
                canonicalPayload,
                "SENDER",
                "{\"requestId\":\"request-replay-1\",\"voucherId\":\"voucher-request-replay\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"10\",\"expiresAt\":\""
                        + expiresAt
                        + "\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"10\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-request-replay\",\"newStateHash\":\""
                        + hash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_fake\",\"timestamp\":\""
                        + now
                        + "\"},\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false}",
                OffsetDateTime.now()
        );
        OfflinePaymentProof existingProof = new OfflinePaymentProof(
                "proof-existing-request-replay",
                "batch-existing-request-replay",
                "voucher-existing-request-replay",
                "collateral-existing-request-replay",
                "device-1",
                "device-9",
                1,
                1,
                1L,
                "nonce-existing-request-replay",
                hash,
                "GENESIS",
                "local_sig_existing",
                new BigDecimal("10"),
                now,
                expiresAt,
                canonicalPayload,
                "SENDER",
                "{\"requestId\":\"request-replay-1\"}",
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

        when(settlementRepository.findById("settlement-request-replay"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-request-replay",
                        "batch-request-replay",
                        "collateral-request-replay",
                        "proof-request-replay",
                        SettlementStatus.REJECTED,
                        "REQUEST_ID_REPLAYED",
                        false,
                        "{\"reasonCode\":\"REQUEST_ID_REPLAYED\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-request-replay")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-request-replay")).thenReturn(Optional.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(proofRepository.findBySenderRequestId("device-1", "request-replay-1")).thenReturn(Optional.of(existingProof));

        SettlementRequest result = service.finalizeSettlement("settlement-request-replay");

        assertEquals(SettlementStatus.REJECTED, result.status());
        assertTrue(result.settlementResultJson().contains("REQUEST_ID_REPLAYED"));
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
        verify(reconciliationCaseRepository).save(
                eq("settlement-6"),
                eq("batch-6"),
                eq("proof-6"),
                eq("voucher-6"),
                eq("PAYLOAD_INVALID"),
                eq(io.korion.offlinepay.domain.status.ReconciliationCaseStatus.OPEN),
                eq("PAYLOAD_AMOUNT_MISMATCH"),
                anyString()
        );
    }

    @Test
    void finalizeSettlementRejectsWhenRequiredPayloadFieldMissing() {
        SettlementRequest request = new SettlementRequest(
                "settlement-7",
                "batch-7",
                "collateral-7",
                "proof-7",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-7",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-7",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        long now = System.currentTimeMillis();
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-7",
                "batch-7",
                "voucher-7",
                "collateral-7",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-7",
                "hash-7",
                "GENESIS",
                "signature",
                new BigDecimal("10"),
                now,
                now + 60_000,
                "{\"voucherId\":\"voucher-7\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-7\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"10\",\"expiresAt\":\"9999999999999\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"10\",\"nonce\":\"nonce-7\",\"newStateHash\":\"hash-7\",\"prevStateHash\":\"GENESIS\",\"signature\":\"signature\",\"timestamp\":\""
                        + now
                        + "\"}}",
                OffsetDateTime.now()
        );

        when(settlementRepository.findById("settlement-7"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-7",
                        "batch-7",
                        "collateral-7",
                        "proof-7",
                        SettlementStatus.REJECTED,
                        "PAYLOAD_REQUIRED_FIELD_MISSING",
                        false,
                        "{\"reasonCode\":\"PAYLOAD_REQUIRED_FIELD_MISSING\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-7")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-7")).thenReturn(Optional.of(proof));

        SettlementRequest result = service.finalizeSettlement("settlement-7");

        assertEquals(SettlementStatus.REJECTED, result.status());
        assertTrue(result.settlementResultJson().contains("PAYLOAD_REQUIRED_FIELD_MISSING"));
        verify(reconciliationCaseRepository).save(
                eq("settlement-7"),
                eq("batch-7"),
                eq("proof-7"),
                eq("voucher-7"),
                eq("PAYLOAD_INVALID"),
                eq(io.korion.offlinepay.domain.status.ReconciliationCaseStatus.OPEN),
                eq("PAYLOAD_REQUIRED_FIELD_MISSING"),
                anyString()
        );
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

    @Test
    void finalizeSettlementCreatesReconciliationCaseWhenLedgerSyncFails() {
        SettlementRequest request = new SettlementRequest(
                "settlement-ledger-fail",
                "batch-ledger-fail",
                "collateral-ledger-fail",
                "proof-ledger-fail",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-ledger-fail",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-ledger-fail",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String proofHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("100"),
                1L,
                "device-1",
                "nonce-ledger-fail"
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-ledger-fail",
                "batch-ledger-fail",
                "voucher-ledger-fail",
                "collateral-ledger-fail",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-ledger-fail",
                proofHash,
                "GENESIS",
                "signature",
                new BigDecimal("100"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{\"voucherId\":\"voucher-ledger-fail\"}",
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

        when(settlementRepository.findById("settlement-ledger-fail"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-ledger-fail",
                        "batch-ledger-fail",
                        "collateral-ledger-fail",
                        "proof-ledger-fail",
                        SettlementStatus.SETTLED,
                        null,
                        false,
                        "{\"releaseAction\":\"RELEASE\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-ledger-fail")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-ledger-fail")).thenReturn(Optional.of(proof));
        when(proofRepository.findByCollateralId("collateral-ledger-fail")).thenReturn(java.util.List.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-ledger-fail")).thenReturn(false);
        SettlementRequest result = service.finalizeSettlement("settlement-ledger-fail");

        assertEquals(SettlementStatus.SETTLED, result.status());
        verify(eventBus).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                eq("settlement-ledger-fail"),
                eq("batch-ledger-fail"),
                eq("proof-ledger-fail"),
                anyString(),
                anyString()
        );
    }

    @Test
    void finalizeSettlementCreatesPartialSettlementCaseWhenHistorySyncFails() {
        SettlementRequest request = new SettlementRequest(
                "settlement-history-fail",
                "batch-history-fail",
                "collateral-history-fail",
                "proof-history-fail",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-history-fail",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-history-fail",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String proofHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("100"),
                1L,
                "device-1",
                "nonce-history-fail"
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-history-fail",
                "batch-history-fail",
                "voucher-history-fail",
                "collateral-history-fail",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-history-fail",
                proofHash,
                "GENESIS",
                "signature",
                new BigDecimal("100"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{\"voucherId\":\"voucher-history-fail\"}",
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

        when(settlementRepository.findById("settlement-history-fail"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-history-fail",
                        "batch-history-fail",
                        "collateral-history-fail",
                        "proof-history-fail",
                        SettlementStatus.SETTLED,
                        null,
                        false,
                        "{\"releaseAction\":\"RELEASE\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-history-fail")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-history-fail")).thenReturn(Optional.of(proof));
        when(proofRepository.findByCollateralId("collateral-history-fail")).thenReturn(java.util.List.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-history-fail")).thenReturn(false);
        SettlementRequest result = service.finalizeSettlement("settlement-history-fail");

        assertEquals(SettlementStatus.SETTLED, result.status());
        verify(eventBus).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                eq("settlement-history-fail"),
                eq("batch-history-fail"),
                eq("proof-history-fail"),
                anyString(),
                anyString()
        );
    }

    @Test
    void finalizeSettlementCreatesLedgerCircuitOpenCase() {
        SettlementRequest request = new SettlementRequest(
                "settlement-ledger-open",
                "batch-ledger-open",
                "collateral-ledger-open",
                "proof-ledger-open",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-ledger-open",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-ledger-open",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String proofHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("100"),
                1L,
                "device-1",
                "nonce-ledger-open"
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-ledger-open",
                "batch-ledger-open",
                "voucher-ledger-open",
                "collateral-ledger-open",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-ledger-open",
                proofHash,
                "GENESIS",
                "signature",
                new BigDecimal("100"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{\"voucherId\":\"voucher-ledger-open\"}",
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

        when(settlementRepository.findById("settlement-ledger-open"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-ledger-open",
                        "batch-ledger-open",
                        "collateral-ledger-open",
                        "proof-ledger-open",
                        SettlementStatus.SETTLED,
                        null,
                        false,
                        "{\"releaseAction\":\"RELEASE\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-ledger-open")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-ledger-open")).thenReturn(Optional.of(proof));
        when(proofRepository.findByCollateralId("collateral-ledger-open")).thenReturn(java.util.List.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-ledger-open")).thenReturn(false);
        service.finalizeSettlement("settlement-ledger-open");

        verify(eventBus).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                eq("settlement-ledger-open"),
                eq("batch-ledger-open"),
                eq("proof-ledger-open"),
                anyString(),
                anyString()
        );
    }

    @Test
    void finalizeSettlementCreatesHistoryCircuitOpenCase() {
        SettlementRequest request = new SettlementRequest(
                "settlement-history-open",
                "batch-history-open",
                "collateral-history-open",
                "proof-history-open",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-history-open",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-history-open",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String proofHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("100"),
                1L,
                "device-1",
                "nonce-history-open"
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-history-open",
                "batch-history-open",
                "voucher-history-open",
                "collateral-history-open",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-history-open",
                proofHash,
                "GENESIS",
                "signature",
                new BigDecimal("100"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{\"voucherId\":\"voucher-history-open\"}",
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

        when(settlementRepository.findById("settlement-history-open"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-history-open",
                        "batch-history-open",
                        "collateral-history-open",
                        "proof-history-open",
                        SettlementStatus.SETTLED,
                        null,
                        false,
                        "{\"releaseAction\":\"RELEASE\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-history-open")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-history-open")).thenReturn(Optional.of(proof));
        when(proofRepository.findByCollateralId("collateral-history-open")).thenReturn(java.util.List.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-history-open")).thenReturn(false);
        service.finalizeSettlement("settlement-history-open");

        verify(eventBus).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                eq("settlement-history-open"),
                eq("batch-history-open"),
                eq("proof-history-open"),
                anyString(),
                anyString()
        );
    }

    @Test
    void finalizeSettlementMapsDuplicateNonceConflictToDuplicateSendCase() {
        SettlementRequest request = new SettlementRequest(
                "settlement-dup-send",
                "batch-dup-send",
                "collateral-dup-send",
                "proof-dup-send",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-dup-send",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                2,
                CollateralStatus.LOCKED,
                "lock-dup-send",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String duplicateNonce = "nonce-dup-send";
        String incomingHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("100"),
                2L,
                "device-1",
                duplicateNonce
        );
        long duplicateNow = System.currentTimeMillis();
        String existingRawPayloadJson = "{\"voucherId\":\"voucher-existing\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"10\",\"expiresAt\":\""
                + (duplicateNow + 60_000)
                + "\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"10\",\"monotonicCounter\":\"1\",\"nonce\":\""
                + duplicateNonce
                + "\",\"newStateHash\":\""
                + spendingProofHashService.computeNewStateHash("GENESIS", new BigDecimal("10"), 1L, "device-1", duplicateNonce)
                + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_existing\",\"timestamp\":\""
                + (duplicateNow - 1_000)
                + "\"}}";
        String incomingRawPayloadJson = "{\"voucherId\":\"voucher-dup-send\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"100\",\"expiresAt\":\""
                + (duplicateNow + 60_000)
                + "\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"100\",\"monotonicCounter\":\"2\",\"nonce\":\""
                + duplicateNonce
                + "\",\"newStateHash\":\""
                + incomingHash
                + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_incoming\",\"timestamp\":\""
                + duplicateNow
                + "\"}}";
        OfflinePaymentProof existingProof = new OfflinePaymentProof(
                "proof-existing",
                "batch-existing",
                "voucher-existing",
                "collateral-dup-send",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                duplicateNonce,
                spendingProofHashService.computeNewStateHash("GENESIS", new BigDecimal("10"), 1L, "device-1", duplicateNonce),
                "GENESIS",
                "local_sig_existing",
                new BigDecimal("10"),
                duplicateNow - 1_000,
                duplicateNow + 60_000,
                "{\"voucherId\":\"voucher-existing\"}",
                "SENDER",
                existingRawPayloadJson,
                OffsetDateTime.now()
        );
        OfflinePaymentProof incomingProof = new OfflinePaymentProof(
                "proof-dup-send",
                "batch-dup-send",
                "voucher-dup-send",
                "collateral-dup-send",
                "device-1",
                "device-2",
                1,
                1,
                2L,
                duplicateNonce,
                incomingHash,
                "GENESIS",
                "local_sig_incoming",
                new BigDecimal("100"),
                duplicateNow,
                duplicateNow + 60_000,
                "{\"voucherId\":\"voucher-dup-send\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                incomingRawPayloadJson,
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

        when(settlementRepository.findById("settlement-dup-send"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-dup-send",
                        "batch-dup-send",
                        "collateral-dup-send",
                        "proof-dup-send",
                        SettlementStatus.CONFLICT,
                        "DUPLICATE_NONCE",
                        true,
                        "{\"reasonCode\":\"DUPLICATE_NONCE\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-dup-send")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-dup-send")).thenReturn(Optional.of(incomingProof));
        when(proofRepository.findByCollateralId("collateral-dup-send")).thenReturn(java.util.List.of(existingProof, incomingProof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-dup-send")).thenReturn(false);
        when(issuedProofVerificationService.verify(any())).thenReturn(
                IssuedProofVerificationService.VerificationResult.valid(
                        new IssuedOfflineProof(
                                "issued-proof-dup-send",
                                77L,
                                "device-1",
                                "collateral-dup-send",
                                "USDT",
                                new BigDecimal("1000"),
                                duplicateNonce,
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

        SettlementRequest result = service.finalizeSettlement("settlement-dup-send");

        assertEquals(SettlementStatus.CONFLICT, result.status());
        verify(reconciliationCaseRepository).save(
                eq("settlement-dup-send"),
                eq("batch-dup-send"),
                eq("proof-dup-send"),
                eq("voucher-dup-send"),
                eq("DUPLICATE_SEND"),
                eq(io.korion.offlinepay.domain.status.ReconciliationCaseStatus.OPEN),
                eq("DUPLICATE_NONCE"),
                anyString()
        );
    }
}
