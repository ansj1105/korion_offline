package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.factory.SettlementBatchFactory;
import io.korion.offlinepay.application.factory.SettlementRequestFactory;
import io.korion.offlinepay.application.factory.SettlementStreamEventFactory;
import io.korion.offlinepay.application.factory.SettlementSyncCommandFactory;
import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.CollateralOperationRepository;
import io.korion.offlinepay.application.port.CollateralRepository;
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
import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.OfflinePayLocalEvidence;
import io.korion.offlinepay.domain.model.OfflineSaga;
import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import io.korion.offlinepay.domain.status.DeviceStatus;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SettlementApplicationServiceTest {

    private static final String DEVICE_ATTESTATION_ID = "attestation-001";
    private static final String DEVICE_ATTESTATION_VERDICT = "HARDWARE_BACKED_VERIFIED";
    private static final String SERVER_VERIFIED_TRUST_LEVEL = "SERVER_VERIFIED";
    private static final String SERVER_ATTESTATION_VERIFIED_AT = "2026-06-11T23:58:00.000Z";
    private static final String TRANSPORT_TRANSCRIPT_SOURCE = "NATIVE_BLE_SEND_TRANSCRIPT_V1";
    private static final String VERIFIED_DEVICE_METADATA = """
            {"deviceAttestationId":"attestation-001","attestationVerdict":"HARDWARE_BACKED_VERIFIED","serverVerifiedTrustLevel":"SERVER_VERIFIED","serverAttestationVerifiedAt":"2026-06-11T23:58:00.000Z"}
            """;

    private final SpendingProofHashService spendingProofHashService = new SpendingProofHashService();

    private final CollateralRepository collateralRepository = Mockito.mock(CollateralRepository.class);
    private final CollateralOperationRepository collateralOperationRepository = Mockito.mock(CollateralOperationRepository.class);
    private final DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
    private final OfflinePayLocalEvidenceRepository localEvidenceRepository = Mockito.mock(OfflinePayLocalEvidenceRepository.class);
    private final OfflinePaymentProofRepository proofRepository = Mockito.mock(OfflinePaymentProofRepository.class);
    private final SettlementBatchRepository batchRepository = Mockito.mock(SettlementBatchRepository.class);
    private final SettlementRepository settlementRepository = Mockito.mock(SettlementRepository.class);
    private final SettlementResultRepository settlementResultRepository = Mockito.mock(SettlementResultRepository.class);
    private final ReconciliationCaseRepository reconciliationCaseRepository = Mockito.mock(ReconciliationCaseRepository.class);
    private final OfflineSagaRepository offlineSagaRepository = Mockito.mock(OfflineSagaRepository.class);
    private final SettlementConflictRepository settlementConflictRepository = Mockito.mock(SettlementConflictRepository.class);
    private final SettlementBatchEventBus eventBus = Mockito.mock(SettlementBatchEventBus.class);
    private final OfflineSagaService offlineSagaService = Mockito.mock(OfflineSagaService.class);
    private final CoinManageSettlementPort coinManageSettlementPort = Mockito.mock(CoinManageSettlementPort.class);
    private final FoxCoinHistoryPort foxCoinHistoryPort = Mockito.mock(FoxCoinHistoryPort.class);
    private final IssuedProofVerificationService issuedProofVerificationService = Mockito.mock(IssuedProofVerificationService.class);
    private final OfflinePayDeviceIdentifierResolver deviceIdentifierResolver = new OfflinePayDeviceIdentifierResolver(deviceRepository);
    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final io.korion.offlinepay.application.service.settlement.OfflinePaySettlementFeeCalculator feeCalculator =
            new io.korion.offlinepay.application.service.settlement.OfflinePaySettlementFeeCalculator();
    private final SettlementApplicationService service = new SettlementApplicationService(
            collateralRepository,
            collateralOperationRepository,
            deviceRepository,
            localEvidenceRepository,
            proofRepository,
            batchRepository,
            settlementRepository,
            settlementResultRepository,
            reconciliationCaseRepository,
            offlineSagaRepository,
            settlementConflictRepository,
            eventBus,
            offlineSagaService,
            coinManageSettlementPort,
            foxCoinHistoryPort,
            jsonService,
            new SettlementBatchFactory(jsonService),
            new SettlementRequestFactory(jsonService),
            new SettlementStreamEventFactory(),
            new SettlementSyncCommandFactory(new ProofFingerprintService(), feeCalculator),
            feeCalculator,
            new ProofSchemaValidator(),
            new ProofPayloadConsistencyValidator(jsonService),
            new ProofConflictDetector(jsonService),
            new ProofChainValidator(jsonService, spendingProofHashService),
            new SettlementPolicyEvaluator(jsonService, new io.korion.offlinepay.application.service.settlement.OfflinePaySettlementFeeCalculator()),
            new DeviceSignatureVerificationService(),
            new DeviceBindingVerificationService(jsonService),
            issuedProofVerificationService,
            deviceIdentifierResolver
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
        Mockito.lenient().when(collateralOperationRepository.saveRequested(
                any(),
                anyLong(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyString(),
                anyString()
        )).thenAnswer(invocation -> new CollateralOperation(
                "op-" + invocation.getArgument(6, String.class),
                invocation.getArgument(0, String.class),
                invocation.getArgument(1, Long.class),
                invocation.getArgument(2, String.class),
                invocation.getArgument(3, String.class),
                invocation.getArgument(4, CollateralOperationType.class),
                invocation.getArgument(5, BigDecimal.class),
                CollateralOperationStatus.REQUESTED,
                invocation.getArgument(6, String.class),
                null,
                invocation.getArgument(7, String.class),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
    }

    @Test
    void submitBatchRejectsDuplicateSenderNonceBeforeCreatingBatch() {
        long now = System.currentTimeMillis();
        OfflinePaymentProof existingProof = new OfflinePaymentProof(
                "proof-existing-nonce-submit",
                "batch-existing-nonce-submit",
                "voucher-existing-nonce-submit",
                "collateral-existing-nonce-submit",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-submit-duplicate",
                "hash-existing",
                "GENESIS",
                "signature-existing",
                new BigDecimal("1"),
                now,
                now + 60_000,
                "{}",
                "SENDER",
                "{\"requestId\":\"request-existing\"}",
                OffsetDateTime.now()
        );
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-new-nonce-submit",
                "collateral-new-nonce-submit",
                "device-1",
                "device-2",
                1,
                1,
                2L,
                "nonce-submit-duplicate",
                "hash-new",
                "GENESIS",
                "signature-new",
                new BigDecimal("1"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.of("requestId", "request-new")
        );
        when(proofRepository.findBySenderNonce("device-1", "nonce-submit-duplicate"))
                .thenReturn(Optional.of(existingProof));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "device-2",
                        "idempotency-new-nonce-submit",
                        java.util.List.of(submission),
                        "MANUAL"
                ))
        );

        assertTrue(exception.getMessage().contains("duplicate offline proof submission by nonce"));
        verify(batchRepository, never()).save(anyString(), anyString(), any(), any(), anyInt(), anyString());
        verify(proofRepository, never()).save(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void submitBatchRejectsDuplicateSenderRequestIdBeforeCreatingBatch() {
        long now = System.currentTimeMillis();
        OfflinePaymentProof existingProof = new OfflinePaymentProof(
                "proof-existing-request-submit",
                "batch-existing-request-submit",
                "voucher-existing-request-submit",
                "collateral-existing-request-submit",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-existing-request-submit",
                "hash-existing",
                "GENESIS",
                "signature-existing",
                new BigDecimal("1"),
                now,
                now + 60_000,
                "{}",
                "SENDER",
                "{\"requestId\":\"request-submit-duplicate\"}",
                OffsetDateTime.now()
        );
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-new-request-submit",
                "collateral-new-request-submit",
                "device-1",
                "device-2",
                1,
                1,
                2L,
                "nonce-new-request-submit",
                "hash-new",
                "GENESIS",
                "signature-new",
                new BigDecimal("1"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.of("requestId", "request-submit-duplicate")
        );
        when(proofRepository.findBySenderRequestId("device-1", "request-submit-duplicate"))
                .thenReturn(Optional.of(existingProof));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "device-2",
                        "idempotency-new-request-submit",
                        java.util.List.of(submission),
                        "MANUAL"
                ))
        );

        assertTrue(exception.getMessage().contains("duplicate offline proof submission by requestId"));
        verify(batchRepository, never()).save(anyString(), anyString(), any(), any(), anyInt(), anyString());
        verify(proofRepository, never()).save(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void receiverDuplicateUploadKeepsReceivedAmountUnsettledUntilExplicitConfirmation() {
        long now = System.currentTimeMillis();
        OfflinePaymentProof existingProof = new OfflinePaymentProof(
                "proof-receiver-confirm",
                "batch-existing-receiver-confirm",
                "voucher-receiver-confirm",
                "collateral-receiver-confirm",
                "sender-device",
                "receiver-device",
                1,
                1,
                7L,
                "nonce-receiver-confirm",
                "hash-receiver-confirm",
                "GENESIS",
                "signature-receiver-confirm",
                new BigDecimal("5.30000000"),
                now,
                now + 60_000,
                "{}",
                "SENDER",
                "BLE",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                "{\"requestId\":\"req-receiver-confirm\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-receiver-confirm",
                "batch-existing-receiver-confirm",
                "collateral-receiver-confirm",
                "proof-receiver-confirm",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementBatch createdBatch = new SettlementBatch(
                "batch-receiver-confirm",
                "receiver-device",
                "idempotency-receiver-confirm",
                SettlementBatchStatus.CREATED,
                null,
                1,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementBatch settledBatch = new SettlementBatch(
                "batch-receiver-confirm",
                "receiver-device",
                "idempotency-receiver-confirm",
                SettlementBatchStatus.SETTLED,
                "SETTLED",
                1,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-receiver-confirm",
                "collateral-receiver-confirm",
                "sender-device",
                "receiver-device",
                1,
                1,
                7L,
                "nonce-receiver-confirm",
                "hash-receiver-confirm",
                "GENESIS",
                "signature-receiver-confirm",
                new BigDecimal("5.30000000"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.ofEntries(
                        java.util.Map.entry("requestId", "req-receiver-confirm"),
                        java.util.Map.entry("receiverLocalBlock", true),
                        java.util.Map.entry("receiverLocalBlockVoucherId", "voucher-receiver-confirm"),
                        java.util.Map.entry("receiverLocalBlockAmount", "5.30000000"),
                        java.util.Map.entry("receiverLocalBlockSenderDeviceId", "sender-device"),
                        java.util.Map.entry("receiverLocalBlockReceiverDeviceId", "receiver-device"),
                        java.util.Map.entry("receiverLocalBlockCounter", 7L),
                        java.util.Map.entry("receiverLocalBlockPrevHash", "GENESIS"),
                        java.util.Map.entry("receiverLocalBlockNewHash", "hash-receiver-confirm"),
                        java.util.Map.entry("receiverLocalBlockNonce", "nonce-receiver-confirm"),
                        java.util.Map.entry("receiverLocalBlockSignature", "signature-receiver-confirm")
                )
        );
        when(proofRepository.findByVoucherId("voucher-receiver-confirm")).thenReturn(Optional.of(existingProof));
        when(settlementRepository.findLatestByProofId("proof-receiver-confirm")).thenReturn(Optional.of(request));
        when(batchRepository.findByIdempotencyKey("idempotency-receiver-confirm")).thenReturn(Optional.empty());
        when(batchRepository.save(
                anyString(),
                anyString(),
                any(SettlementBatchStatus.class),
                any(),
                anyInt(),
                anyString()
        )).thenReturn(createdBatch);
        when(batchRepository.findById("batch-receiver-confirm")).thenReturn(Optional.of(settledBatch));

        SettlementBatch result = service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                SettlementApplicationService.UploaderType.RECEIVER,
                "receiver-device",
                "idempotency-receiver-confirm",
                java.util.List.of(submission),
                "MANUAL"
        ));

        assertEquals(SettlementBatchStatus.SETTLED, result.status());
        verify(localEvidenceRepository).save(argThat(evidence ->
                "proof-receiver-confirm".equals(evidence.proofId())
                        && "RECEIVE".equals(evidence.direction())
                        && "receiver-device".equals(evidence.uploaderDeviceId())
                        && "hash-receiver-confirm".equals(evidence.hashChainHead())
                        && "VERIFIED".equals(evidence.verificationStatus())
        ));
        verify(proofRepository).ensureReceivedUnsettledAmount("proof-receiver-confirm", new BigDecimal("5.294700"));
        verify(coinManageSettlementPort, never()).finalizeSettlement(any(CoinManageSettlementPort.SettlementLedgerCommand.class));
        verify(eventBus, never()).publishExternalSyncRequested(
                eq("RECEIVER_HISTORY_SYNC_REQUESTED"),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void submitBatchStoresSenderLocalEvidenceWithPayloadReceiverUserWhenDeviceOwnerChanged() {
        long now = System.currentTimeMillis();
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-sender-evidence",
                "collateral-sender-evidence",
                "sender-device",
                "receiver-device",
                1,
                1,
                11L,
                "nonce-sender-evidence",
                "hash-sender-evidence",
                "prev-sender-evidence",
                "signature-sender-evidence",
                new BigDecimal("2.50000000"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.ofEntries(
                        java.util.Map.entry("requestId", "req-sender-evidence"),
                        java.util.Map.entry("senderUserId", 77L),
                        java.util.Map.entry("receiverUserId", 88L),
                        java.util.Map.entry("senderLocalBlock", true),
                        java.util.Map.entry("senderLocalBlockVoucherId", "voucher-sender-evidence"),
                        java.util.Map.entry("senderLocalBlockAmount", "2.50000000"),
                        java.util.Map.entry("senderLocalBlockSenderDeviceId", "sender-device"),
                        java.util.Map.entry("senderLocalBlockReceiverDeviceId", "receiver-device"),
                        java.util.Map.entry("senderLocalBlockCounter", 11L),
                        java.util.Map.entry("senderLocalBlockPrevHash", "prev-sender-evidence"),
                        java.util.Map.entry("senderLocalBlockNewHash", "hash-sender-evidence"),
                        java.util.Map.entry("senderLocalBlockNonce", "nonce-sender-evidence"),
                        java.util.Map.entry("senderLocalBlockSignature", "signature-sender-evidence")
                )
        );
        SettlementBatch createdBatch = new SettlementBatch(
                "batch-sender-evidence",
                "sender-device",
                "idempotency-sender-evidence",
                SettlementBatchStatus.CREATED,
                null,
                1,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementBatch uploadedBatch = new SettlementBatch(
                "batch-sender-evidence",
                "sender-device",
                "idempotency-sender-evidence",
                SettlementBatchStatus.UPLOADED,
                null,
                1,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-sender-evidence",
                77L,
                "sender-device",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-sender-evidence",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-sender-evidence",
                "batch-sender-evidence",
                "voucher-sender-evidence",
                "collateral-sender-evidence",
                "sender-device",
                "receiver-device",
                1,
                1,
                11L,
                "nonce-sender-evidence",
                "hash-sender-evidence",
                "prev-sender-evidence",
                "signature-sender-evidence",
                new BigDecimal("2.50000000"),
                now,
                now + 60_000,
                "{}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-sender-evidence",
                "batch-sender-evidence",
                "collateral-sender-evidence",
                "proof-sender-evidence",
                SettlementStatus.PENDING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(proofRepository.findByVoucherId("voucher-sender-evidence")).thenReturn(Optional.empty());
        when(batchRepository.findByIdempotencyKey("idempotency-sender-evidence")).thenReturn(Optional.empty());
        when(batchRepository.save(anyString(), anyString(), any(), any(), anyInt(), anyString())).thenReturn(createdBatch);
        when(collateralRepository.findById("collateral-sender-evidence")).thenReturn(Optional.of(collateral));
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(new Device(
                "row-receiver-sender-evidence",
                "receiver-device",
                1474L,
                "receiver-public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));
        when(proofRepository.save(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyLong(),
                anyInt(), anyInt(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn(proof);
        when(settlementRepository.save(anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(request);
        when(batchRepository.findById("batch-sender-evidence")).thenReturn(Optional.of(uploadedBatch));

        SettlementBatch result = service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                SettlementApplicationService.UploaderType.SENDER,
                "sender-device",
                "idempotency-sender-evidence",
                java.util.List.of(submission),
                "BLE_OFFLINE_SYNC"
        ));

        assertEquals(SettlementBatchStatus.UPLOADED, result.status());
        verify(proofRepository).save(
                eq("batch-sender-evidence"),
                eq("voucher-sender-evidence"),
                eq("collateral-sender-evidence"),
                eq("sender-device"),
                eq("receiver-device"),
                eq(77L),
                eq(88L),
                eq(1),
                eq(1),
                eq(11L),
                eq("nonce-sender-evidence"),
                eq("hash-sender-evidence"),
                eq("prev-sender-evidence"),
                eq("signature-sender-evidence"),
                eq(new BigDecimal("2.50000000")),
                eq(now),
                eq(now + 60_000),
                eq("{}"),
                eq("SENDER"),
                anyString(),
                anyString()
        );
        verify(localEvidenceRepository).save(argThat(evidence ->
                "proof-sender-evidence".equals(evidence.proofId())
                        && "SEND".equals(evidence.direction())
                        && "sender-device".equals(evidence.uploaderDeviceId())
                        && "hash-sender-evidence".equals(evidence.hashChainHead())
                        && "PENDING".equals(evidence.verificationStatus())
        ));
    }

    @Test
    void receiverDuplicateUploadWithAutoSettlementRequestsReceiverHistorySync() {
        long now = System.currentTimeMillis();
        OfflinePaymentProof existingProof = new OfflinePaymentProof(
                "proof-receiver-auto-confirm",
                "batch-existing-receiver-auto-confirm",
                "voucher-receiver-auto-confirm",
                "collateral-receiver-auto-confirm",
                "sender-device",
                "receiver-device",
                77L,
                88L,
                1,
                1,
                7L,
                "nonce-receiver-auto-confirm",
                "hash-receiver-auto-confirm",
                "GENESIS",
                "signature-receiver-auto-confirm",
                new BigDecimal("5.30000000"),
                now,
                now + 60_000,
                "{}",
                "SENDER",
                "BLE",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                "{\"requestId\":\"req-receiver-auto-confirm\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-receiver-auto-confirm",
                "batch-existing-receiver-auto-confirm",
                "collateral-receiver-auto-confirm",
                "proof-receiver-auto-confirm",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementBatch createdBatch = new SettlementBatch(
                "batch-receiver-auto-confirm",
                "receiver-device",
                "idempotency-receiver-auto-confirm",
                SettlementBatchStatus.CREATED,
                null,
                1,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementBatch settledBatch = new SettlementBatch(
                "batch-receiver-auto-confirm",
                "receiver-device",
                "idempotency-receiver-auto-confirm",
                SettlementBatchStatus.SETTLED,
                "SETTLED",
                1,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-receiver-auto-confirm",
                "collateral-receiver-auto-confirm",
                "sender-device",
                "receiver-device",
                1,
                1,
                7L,
                "nonce-receiver-auto-confirm",
                "hash-receiver-auto-confirm",
                "GENESIS",
                "signature-receiver-auto-confirm",
                new BigDecimal("5.30000000"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.of(
                        "requestId", "req-receiver-auto-confirm",
                        "receiverSettlementMode", "AUTO",
                        "receiverSettlementAutoEnabled", true
                )
        );
        Device receiverDevice = new Device(
                "row-receiver-auto-confirm",
                "receiver-device",
                88L,
                "receiver-public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-receiver-auto-confirm",
                77L,
                "sender-device",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-receiver-auto-confirm",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(proofRepository.findByVoucherId("voucher-receiver-auto-confirm")).thenReturn(Optional.of(existingProof));
        when(settlementRepository.findLatestByProofId("proof-receiver-auto-confirm")).thenReturn(Optional.of(request));
        when(batchRepository.findByIdempotencyKey("idempotency-receiver-auto-confirm")).thenReturn(Optional.empty());
        when(batchRepository.save(
                anyString(),
                anyString(),
                any(SettlementBatchStatus.class),
                any(),
                anyInt(),
                anyString()
        )).thenReturn(createdBatch);
        when(batchRepository.findById("batch-receiver-auto-confirm")).thenReturn(Optional.of(settledBatch));
        when(offlineSagaRepository.findBySagaTypeAndReferenceId(OfflineSagaType.SETTLEMENT, "settlement-receiver-auto-confirm"))
                .thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));
        when(collateralRepository.findById("collateral-receiver-auto-confirm")).thenReturn(Optional.of(collateral));
        when(coinManageSettlementPort.finalizeSettlement(any(CoinManageSettlementPort.SettlementLedgerCommand.class)))
                .thenReturn(new CoinManageSettlementPort.SettlementLedgerResult(
                        "settlement-receiver-auto-confirm",
                        "FINALIZED",
                        "RELEASE",
                        true,
                        "SENDER",
                        "LEDGER_AND_EXTERNAL_HISTORY_SYNC",
                        "SENDER_LEDGER_PLUS_RECEIVER_LEDGER_AND_HISTORY",
                        "OFFLINE_PAY_SAGA",
                        new BigDecimal("0.000000"),
                        new BigDecimal("0.000000"),
                        new BigDecimal("0.000000")
                ));

        SettlementBatch result = service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                SettlementApplicationService.UploaderType.RECEIVER,
                "receiver-device",
                "idempotency-receiver-auto-confirm",
                java.util.List.of(submission),
                "BLE_OFFLINE_SYNC"
        ));

        assertEquals(SettlementBatchStatus.SETTLED, result.status());
        verify(proofRepository).ensureReceivedUnsettledAmount("proof-receiver-auto-confirm", new BigDecimal("5.294700"));
        verify(coinManageSettlementPort).finalizeSettlement(argThat(command ->
                command.receiverWalletSettlementRequested()
                        && Long.valueOf(88L).equals(command.receiverUserId())
                        && "receiver-device".equals(command.receiverDeviceId())
        ));
        verify(eventBus).publishExternalSyncRequested(
                eq("RECEIVER_HISTORY_SYNC_REQUESTED"),
                eq("settlement-receiver-auto-confirm"),
                eq("batch-existing-receiver-auto-confirm"),
                eq("proof-receiver-auto-confirm"),
                argThat(payload -> payload.contains("\"receiverHistoryCommand\"")
                        && payload.contains("\"receiverWalletSettlementRequested\":true")
                        && payload.contains("\"receiverOnlineConfirmedAt\"")),
                anyString()
        );
    }

    @Test
    void confirmReceivedSettlementsRequestsReceiverWalletSettlementForManualRecovery() {
        long now = System.currentTimeMillis();
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-receiver-manual-confirm",
                "batch-receiver-manual-confirm",
                "voucher-receiver-manual-confirm",
                "collateral-receiver-manual-confirm",
                "sender-device",
                "receiver-device",
                77L,
                88L,
                1,
                1,
                12L,
                "nonce-receiver-manual-confirm",
                "hash-receiver-manual-confirm",
                "prev-receiver-manual-confirm",
                "signature-receiver-manual-confirm",
                new BigDecimal("2.00000000"),
                now,
                now + 60_000,
                "{}",
                "RECEIVER",
                "BLE",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                new BigDecimal("1.99800000"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                "{\"requestId\":\"req-receiver-manual-confirm\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-receiver-manual-confirm",
                "batch-receiver-manual-confirm",
                "collateral-receiver-manual-confirm",
                "proof-receiver-manual-confirm",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        Device receiverDevice = new Device(
                "row-receiver-manual-confirm",
                "receiver-device",
                88L,
                "receiver-public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-receiver-manual-confirm",
                77L,
                "sender-device",
                "KORI",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-receiver-manual-confirm",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(proofRepository.findById("proof-receiver-manual-confirm")).thenReturn(Optional.of(proof));
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));
        when(settlementRepository.findLatestByProofId("proof-receiver-manual-confirm")).thenReturn(Optional.of(request));
        when(offlineSagaRepository.findBySagaTypeAndReferenceId(
                OfflineSagaType.SETTLEMENT,
                "settlement-receiver-manual-confirm"
        )).thenReturn(Optional.empty());
        when(collateralRepository.findById("collateral-receiver-manual-confirm")).thenReturn(Optional.of(collateral));
        when(coinManageSettlementPort.finalizeSettlement(any(CoinManageSettlementPort.SettlementLedgerCommand.class)))
                .thenReturn(new CoinManageSettlementPort.SettlementLedgerResult(
                        "settlement-receiver-manual-confirm",
                        "FINALIZED",
                        "RELEASE",
                        true,
                        "SENDER",
                        "LEDGER_AND_EXTERNAL_HISTORY_SYNC",
                        "SENDER_LEDGER_PLUS_RECEIVER_LEDGER_AND_HISTORY",
                        "OFFLINE_PAY_SAGA",
                        new BigDecimal("0.000000"),
                        new BigDecimal("0.000000"),
                        new BigDecimal("0.000000")
                ));

        SettlementApplicationService.ReceiverSettlementConfirmationResult result =
                service.confirmReceivedSettlements(new SettlementApplicationService.ConfirmReceivedSettlementsCommand(
                        88L,
                        java.util.List.of("proof-receiver-manual-confirm")
                ));

        assertEquals(1, result.requested());
        assertEquals(1, result.confirmed());
        assertEquals(0, result.skipped());
        verify(coinManageSettlementPort).finalizeSettlement(argThat(command ->
                command.receiverWalletSettlementRequested()
                        && Long.valueOf(88L).equals(command.receiverUserId())
                        && "receiver-device".equals(command.receiverDeviceId())
        ));
        verify(eventBus).publishExternalSyncRequested(
                eq("RECEIVER_HISTORY_SYNC_REQUESTED"),
                eq("settlement-receiver-manual-confirm"),
                eq("batch-receiver-manual-confirm"),
                eq("proof-receiver-manual-confirm"),
                argThat(payload -> payload.contains("\"receiverWalletSettlementRequested\":true")
                        && payload.contains("\"receiverOnlineConfirmedAt\"")
                        && payload.contains("\"receiverHistoryCommand\"")),
                anyString()
        );
    }

    @Test
    void confirmReceivedSettlementsClosesUnsettledMarkerWhenReceiverHistoryAlreadySynced() {
        long now = System.currentTimeMillis();
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-receiver-history-synced",
                "batch-receiver-history-synced",
                "voucher-receiver-history-synced",
                "collateral-receiver-history-synced",
                "sender-device",
                "receiver-device",
                77L,
                88L,
                1,
                1,
                12L,
                "nonce-receiver-history-synced",
                "hash-receiver-history-synced",
                "prev-receiver-history-synced",
                "signature-receiver-history-synced",
                new BigDecimal("2.00000000"),
                now,
                now + 60_000,
                "{}",
                "RECEIVER",
                "BLE",
                OfflineProofStatus.SETTLED,
                "SETTLED",
                new BigDecimal("1.99800000"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                "{\"requestId\":\"req-receiver-history-synced\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-receiver-history-synced",
                "batch-receiver-history-synced",
                "collateral-receiver-history-synced",
                "proof-receiver-history-synced",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        Device receiverDevice = new Device(
                "row-receiver-history-synced",
                "receiver-device",
                88L,
                "receiver-public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflineSaga saga = new OfflineSaga(
                "saga-receiver-history-synced",
                OfflineSagaType.SETTLEMENT,
                "settlement-receiver-history-synced",
                OfflineSagaStatus.COMPLETED,
                "RECEIVER_HISTORY_SYNCED",
                "SETTLED",
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(proofRepository.findById("proof-receiver-history-synced")).thenReturn(Optional.of(proof));
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));
        when(settlementRepository.findLatestByProofId("proof-receiver-history-synced")).thenReturn(Optional.of(request));
        when(offlineSagaRepository.findBySagaTypeAndReferenceId(
                OfflineSagaType.SETTLEMENT,
                "settlement-receiver-history-synced"
        )).thenReturn(Optional.of(saga));

        SettlementApplicationService.ReceiverSettlementConfirmationResult result =
                service.confirmReceivedSettlements(new SettlementApplicationService.ConfirmReceivedSettlementsCommand(
                        88L,
                        java.util.List.of("proof-receiver-history-synced")
                ));

        assertEquals(1, result.requested());
        assertEquals(1, result.confirmed());
        assertEquals(0, result.skipped());
        verify(proofRepository).markReceivedCollateralSettled(
                eq(java.util.List.of("proof-receiver-history-synced")),
                isNull(),
                eq("wallet:settlement-receiver-history-synced")
        );
        verify(eventBus, never()).publishExternalSyncRequested(
                eq("RECEIVER_HISTORY_SYNC_REQUESTED"),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void submitBatchRejectsMismatchedReceiverLocalBlockBeforeCreatingBatch() {
        long now = System.currentTimeMillis();
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-receiver-block",
                "collateral-receiver-block",
                "sender-device",
                "receiver-device",
                1,
                1,
                7L,
                "nonce-receiver-block",
                "hash-receiver-block",
                "GENESIS",
                "signature-receiver-block",
                new BigDecimal("5.30000000"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.ofEntries(
                        java.util.Map.entry("requestId", "req-receiver-block"),
                        java.util.Map.entry("receiverLocalBlock", true),
                        java.util.Map.entry("receiverLocalBlockVoucherId", "voucher-receiver-block"),
                        java.util.Map.entry("receiverLocalBlockAmount", "5.30000000"),
                        java.util.Map.entry("receiverLocalBlockSenderDeviceId", "sender-device"),
                        java.util.Map.entry("receiverLocalBlockReceiverDeviceId", "receiver-device"),
                        java.util.Map.entry("receiverLocalBlockCounter", 7L),
                        java.util.Map.entry("receiverLocalBlockPrevHash", "GENESIS"),
                        java.util.Map.entry("receiverLocalBlockNewHash", "different-hash"),
                        java.util.Map.entry("receiverLocalBlockNonce", "nonce-receiver-block"),
                        java.util.Map.entry("receiverLocalBlockSignature", "signature-receiver-block")
                )
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-receiver-block",
                        java.util.List.of(submission),
                        "BLE_OFFLINE_SYNC"
                ))
        );

        assertTrue(exception.getMessage().contains("receiverLocalBlock NewHash mismatch"));
        verify(batchRepository, never()).save(
                anyString(),
                anyString(),
                any(SettlementBatchStatus.class),
                any(),
                anyInt(),
                anyString()
        );
    }

    @Test
    void submitBatchRejectsInvalidReceiverEvidenceBlockSignatureBeforeCreatingBatch() throws Exception {
        long now = System.currentTimeMillis();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair receiverKeyPair = generator.generateKeyPair();
        KeyPair otherKeyPair = generator.generateKeyPair();
        String canonicalPayload = """
                {"amount":"5.30000000","assetCode":"KORI","counter":3,"createdAt":"2026-06-12T00:00:00.000Z","deviceId":"receiver-device","direction":"RECEIVE","fee":"0.005300","nonce":"nonce-receiver-block","payload":{"senderProofCounter":7},"prevHash":"GENESIS","proofId":"proof-receiver-block","receiverDeviceId":"receiver-device","senderDeviceId":"sender-device","sessionId":"req-receiver-block","source":"SENDER_COMPLETE","userId":"1","voucherId":"voucher-receiver-block"}
                """.trim();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(otherKeyPair.getPrivate());
        signer.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        String invalidSignature = Base64.getEncoder().encodeToString(signer.sign());
        Device receiverDevice = new Device(
                "receiver-row",
                "receiver-device",
                1L,
                Base64.getEncoder().encodeToString(receiverKeyPair.getPublic().getEncoded()),
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-receiver-block",
                "collateral-receiver-block",
                "sender-device",
                "receiver-device",
                1,
                1,
                7L,
                "nonce-sender-proof",
                "hash-sender-proof",
                "prev-sender-proof",
                "signature-sender-proof",
                new BigDecimal("5.30000000"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.ofEntries(
                        java.util.Map.entry("requestId", "req-receiver-block"),
                        java.util.Map.entry("receiverLocalBlock", true),
                        java.util.Map.entry("receiverLocalBlockVoucherId", "voucher-receiver-block"),
                        java.util.Map.entry("receiverLocalBlockAmount", "5.30000000"),
                        java.util.Map.entry("receiverLocalBlockSenderDeviceId", "sender-device"),
                        java.util.Map.entry("receiverLocalBlockReceiverDeviceId", "receiver-device"),
                        java.util.Map.entry("receiverLocalBlockCounter", 7L),
                        java.util.Map.entry("receiverLocalBlockPrevHash", "prev-sender-proof"),
                        java.util.Map.entry("receiverLocalBlockNewHash", "hash-sender-proof"),
                        java.util.Map.entry("receiverLocalBlockNonce", "nonce-sender-proof"),
                        java.util.Map.entry("receiverLocalBlockSignature", "signature-sender-proof"),
                        java.util.Map.entry("receiverEvidenceBlock", true),
                        java.util.Map.entry("receiverEvidenceBlockCounter", 3L),
                        java.util.Map.entry("receiverEvidenceBlockPrevHash", "GENESIS"),
                        java.util.Map.entry("receiverEvidenceBlockNewHash", sha256Hex(canonicalPayload)),
                        java.util.Map.entry("receiverEvidenceBlockNonce", "nonce-receiver-block"),
                        java.util.Map.entry("receiverEvidenceBlockSignature", invalidSignature),
                        java.util.Map.entry("receiverEvidenceBlockCanonicalPayload", canonicalPayload),
                        java.util.Map.entry("receiverEvidenceBlockSenderProofCounter", 7L),
                        java.util.Map.entry("receiverEvidenceBlockSenderProofPrevHash", "prev-sender-proof"),
                        java.util.Map.entry("receiverEvidenceBlockSenderProofNewHash", "hash-sender-proof"),
                        java.util.Map.entry("receiverEvidenceBlockSenderProofNonce", "nonce-sender-proof"),
                        java.util.Map.entry("receiverEvidenceBlockSenderProofSignature", "signature-sender-proof")
                )
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-receiver-evidence-block",
                        java.util.List.of(submission),
                        "BLE_OFFLINE_SYNC"
                ))
        );

        assertTrue(exception.getMessage().contains("receiverEvidenceBlock signature invalid"));
        verify(batchRepository, never()).save(anyString(), anyString(), any(), any(), anyInt(), anyString());
    }

    @Test
    void submitBatchRejectsReceiverOnlyEvidenceBeforeCreatingProof() {
        long now = System.currentTimeMillis();
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-receiver-only",
                "collateral-receiver-only",
                "sender-device",
                "receiver-device",
                1,
                1,
                8L,
                "nonce-receiver-only",
                "hash-receiver-only",
                "GENESIS",
                "signature-receiver-only",
                new BigDecimal("4.20000000"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.ofEntries(
                        java.util.Map.entry("requestId", "req-receiver-only"),
                        java.util.Map.entry("receiverLocalBlock", true),
                        java.util.Map.entry("receiverLocalBlockVoucherId", "voucher-receiver-only"),
                        java.util.Map.entry("receiverLocalBlockAmount", "4.20000000"),
                        java.util.Map.entry("receiverLocalBlockSenderDeviceId", "sender-device"),
                        java.util.Map.entry("receiverLocalBlockReceiverDeviceId", "receiver-device"),
                        java.util.Map.entry("receiverLocalBlockCounter", 8L),
                        java.util.Map.entry("receiverLocalBlockPrevHash", "GENESIS"),
                        java.util.Map.entry("receiverLocalBlockNewHash", "hash-receiver-only"),
                        java.util.Map.entry("receiverLocalBlockNonce", "nonce-receiver-only"),
                        java.util.Map.entry("receiverLocalBlockSignature", "signature-receiver-only"),
                        java.util.Map.entry("receiverSettlementMode", "MANUAL"),
                        java.util.Map.entry("receiverSettlementAutoEnabled", false)
                )
        );
        when(proofRepository.findByVoucherId("voucher-receiver-only")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-receiver-only",
                        java.util.List.of(submission),
                        "BLE_OFFLINE_SYNC"
                ))
        );

        assertTrue(exception.getMessage().contains("receiver settlement requires existing sender proof"));
        verify(batchRepository, never()).save(
                anyString(),
                anyString(),
                any(SettlementBatchStatus.class),
                any(),
                anyInt(),
                anyString()
        );
        verify(proofRepository, never()).save(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void ingestLocalEvidenceStoresReceiverEvidenceWithoutCreatingSettlement() {
        String transportSessionHash = "a".repeat(64);
        SettlementApplicationService.LocalEvidenceIngestResult result = service.ingestLocalEvidence(
                new SettlementApplicationService.LocalEvidenceIngestCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-local-evidence-receiver",
                        java.util.List.of(new SettlementApplicationService.LocalEvidenceSubmission(
                                "voucher-local-evidence",
                                "req-local-evidence",
                                "RECEIVE",
                                "sender-device",
                                "receiver-device",
                                new BigDecimal("2.10000000"),
                                11L,
                                "prev-local-evidence",
                                "hash-local-evidence",
                                "nonce-local-evidence",
                                "signature-local-evidence",
                                "{\"voucherId\":\"voucher-local-evidence\"}",
                                "merchant-001",
                                "partner-kr-01",
                                "leader-kr",
                                "kr",
                                "store-seoul-001",
                                "order-123",
                                "pi-123",
                                "inv-123",
                                "12500",
                                "krw",
                                "2500",
                                "2026-06-11T23:59:30.000Z",
                                "1",
                                "1",
                                "SHA-256",
                                "SHA256withECDSA",
                                "device-key-001",
                                "pubkey-fingerprint-001",
                                "1.8.1",
                                DEVICE_ATTESTATION_ID,
                                DEVICE_ATTESTATION_VERDICT,
                                SERVER_VERIFIED_TRUST_LEVEL,
                                SERVER_ATTESTATION_VERIFIED_AT,
                                transportSessionHash,
                                TRANSPORT_TRANSCRIPT_SOURCE,
                                null,
                                null,
                                java.util.Map.of("receiverEvidenceBlock", true)
                        ))
                )
        );

        assertEquals(1, result.requested());
        assertEquals(0, result.stored());
        assertEquals(1, result.skipped());
        assertEquals(0, result.matched());
        assertEquals(0, result.awaitingCarrier());
        verify(localEvidenceRepository).save(argThat(evidence ->
                evidence.proofId() == null
                        && "voucher-local-evidence".equals(evidence.voucherId())
                        && "RECEIVE".equals(evidence.direction())
                        && "RECEIVER".equals(evidence.uploaderType())
                        && "receiver-device".equals(evidence.uploaderDeviceId())
                        && "FAILED".equals(evidence.verificationStatus())
                        && evidence.verificationDetail().contains("hash mismatch")
                        && "merchant-001".equals(evidence.rawPayload().get("merchantId"))
                        && "partner-kr-01".equals(evidence.rawPayload().get("partnerId"))
                        && "leader-kr".equals(evidence.rawPayload().get("leaderId"))
                        && "KR".equals(evidence.rawPayload().get("countryCode"))
                        && "store-seoul-001".equals(evidence.rawPayload().get("storeId"))
                        && "order-123".equals(evidence.rawPayload().get("orderId"))
                        && "pi-123".equals(evidence.rawPayload().get("paymentIntentId"))
                        && "inv-123".equals(evidence.rawPayload().get("invoiceId"))
                        && "12500".equals(evidence.rawPayload().get("fiatAmount"))
                        && "KRW".equals(evidence.rawPayload().get("fiatCurrency"))
                        && "2500".equals(evidence.rawPayload().get("exchangeRate"))
                        && "2026-06-11T23:59:30.000Z".equals(evidence.rawPayload().get("rateTimestamp"))
                        && "1".equals(evidence.rawPayload().get("schemaVersion"))
                        && "1".equals(evidence.rawPayload().get("protocolVersion"))
                        && "SHA-256".equals(evidence.rawPayload().get("hashAlgorithm"))
                        && "SHA256withECDSA".equals(evidence.rawPayload().get("signatureAlgorithm"))
                        && "device-key-001".equals(evidence.rawPayload().get("keyId"))
                        && "pubkey-fingerprint-001".equals(evidence.rawPayload().get("publicKeyFingerprint"))
                        && "1.8.1".equals(evidence.rawPayload().get("appVersion"))
                        && "attestation-001".equals(evidence.rawPayload().get("deviceAttestationId"))
                        && transportSessionHash.equals(evidence.rawPayload().get("transportSessionHash"))
                        && evidence.matchedProofId() == null
        ));
        verifyNoInteractions(batchRepository, settlementRepository);
    }

    @Test
    void ingestLocalEvidenceStoresVerifiedReceiverEvidenceWithoutCreatingSettlementByItself() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair receiverKeyPair = generator.generateKeyPair();
        String transportTranscript = "BLE:req-local-evidence-verified:sender-device:receiver-device:2.10000000";
        String transportSessionHash = sha256Hex(transportTranscript);
        String canonicalPayload = """
                {"voucherId":"voucher-local-evidence-verified","direction":"RECEIVE","deviceId":"receiver-device","senderDeviceId":"sender-device","receiverDeviceId":"receiver-device","prevHash":"prev-local-evidence-verified","nonce":"nonce-local-evidence-verified","deviceAttestationId":"%s","deviceAttestationVerdict":"%s","serverVerifiedTrustLevel":"%s","serverAttestationVerifiedAt":"%s","transportSessionHash":"%s","transportTranscriptHash":"%s","transportTranscriptSource":"%s","counter":13,"amount":"2.10000000"}
                """.formatted(DEVICE_ATTESTATION_ID, DEVICE_ATTESTATION_VERDICT, SERVER_VERIFIED_TRUST_LEVEL, SERVER_ATTESTATION_VERIFIED_AT, transportSessionHash, transportSessionHash, TRANSPORT_TRANSCRIPT_SOURCE).trim();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(receiverKeyPair.getPrivate());
        signer.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String receiverPublicKey = Base64.getEncoder().encodeToString(receiverKeyPair.getPublic().getEncoded());
        Device receiverDevice = new Device(
                "receiver-row-verified",
                "receiver-device",
                1L,
                receiverPublicKey,
                1,
                DeviceStatus.ACTIVE,
                VERIFIED_DEVICE_METADATA,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));

        SettlementApplicationService.LocalEvidenceIngestResult result = service.ingestLocalEvidence(
                new SettlementApplicationService.LocalEvidenceIngestCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-local-evidence-receiver-verified",
                        java.util.List.of(new SettlementApplicationService.LocalEvidenceSubmission(
                                "voucher-local-evidence-verified",
                                "req-local-evidence-verified",
                                "RECEIVE",
                                "sender-device",
                                "receiver-device",
                                new BigDecimal("2.10000000"),
                                13L,
                                "prev-local-evidence-verified",
                                sha256Hex(canonicalPayload),
                                "nonce-local-evidence-verified",
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
                                "1",
                                "1",
                                "SHA-256",
                                "SHA256withECDSA",
                                "device:receiver-device:v1",
                                sha256Hex(receiverPublicKey),
                                null,
                                DEVICE_ATTESTATION_ID,
                                DEVICE_ATTESTATION_VERDICT,
                                SERVER_VERIFIED_TRUST_LEVEL,
                                SERVER_ATTESTATION_VERIFIED_AT,
                                transportSessionHash,
                                TRANSPORT_TRANSCRIPT_SOURCE,
                                transportTranscript,
                                "UTF-8",
                                java.util.Map.of("receiverEvidenceBlock", true)
                        ))
                )
        );

        assertEquals(1, result.requested());
        assertEquals(1, result.stored());
        assertEquals(0, result.skipped());
        assertEquals(0, result.matched());
        assertEquals(1, result.awaitingCarrier());
        verify(localEvidenceRepository).save(argThat(evidence ->
                evidence.proofId() == null
                        && "voucher-local-evidence-verified".equals(evidence.voucherId())
                        && "RECEIVE".equals(evidence.direction())
                        && "VERIFIED".equals(evidence.verificationStatus())
                        && transportTranscript.equals(evidence.rawPayload().get("transportTranscript"))
                        && "UTF-8".equals(evidence.rawPayload().get("transportTranscriptEncoding"))
                        && evidence.matchedProofId() == null
        ));
        verify(proofRepository).findByVoucherId("voucher-local-evidence-verified");
        verifyNoInteractions(batchRepository, settlementRepository);
    }

    @Test
    void ingestLocalEvidenceRejectsNativeTransportHashWithoutTranscript() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair receiverKeyPair = generator.generateKeyPair();
        String transportSessionHash = "b".repeat(64);
        String canonicalPayload = """
                {"voucherId":"voucher-local-evidence-no-transcript","direction":"RECEIVE","deviceId":"receiver-device","senderDeviceId":"sender-device","receiverDeviceId":"receiver-device","prevHash":"prev-local-evidence-no-transcript","nonce":"nonce-local-evidence-no-transcript","deviceAttestationId":"%s","deviceAttestationVerdict":"%s","serverVerifiedTrustLevel":"%s","serverAttestationVerifiedAt":"%s","transportSessionHash":"%s","transportTranscriptHash":"%s","transportTranscriptSource":"%s","counter":17,"amount":"2.10000000"}
                """.formatted(DEVICE_ATTESTATION_ID, DEVICE_ATTESTATION_VERDICT, SERVER_VERIFIED_TRUST_LEVEL, SERVER_ATTESTATION_VERIFIED_AT, transportSessionHash, transportSessionHash, TRANSPORT_TRANSCRIPT_SOURCE).trim();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(receiverKeyPair.getPrivate());
        signer.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String receiverPublicKey = Base64.getEncoder().encodeToString(receiverKeyPair.getPublic().getEncoded());
        Device receiverDevice = new Device(
                "receiver-row-no-transcript",
                "receiver-device",
                1L,
                receiverPublicKey,
                1,
                DeviceStatus.ACTIVE,
                VERIFIED_DEVICE_METADATA,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));

        SettlementApplicationService.LocalEvidenceIngestResult result = service.ingestLocalEvidence(
                new SettlementApplicationService.LocalEvidenceIngestCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-local-evidence-no-transcript",
                        java.util.List.of(new SettlementApplicationService.LocalEvidenceSubmission(
                                "voucher-local-evidence-no-transcript",
                                "req-local-evidence-no-transcript",
                                "RECEIVE",
                                "sender-device",
                                "receiver-device",
                                new BigDecimal("2.10000000"),
                                17L,
                                "prev-local-evidence-no-transcript",
                                sha256Hex(canonicalPayload),
                                "nonce-local-evidence-no-transcript",
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
                                "1",
                                "1",
                                "SHA-256",
                                "SHA256withECDSA",
                                "device:receiver-device:v1",
                                sha256Hex(receiverPublicKey),
                                null,
                                DEVICE_ATTESTATION_ID,
                                DEVICE_ATTESTATION_VERDICT,
                                SERVER_VERIFIED_TRUST_LEVEL,
                                SERVER_ATTESTATION_VERIFIED_AT,
                                transportSessionHash,
                                TRANSPORT_TRANSCRIPT_SOURCE,
                                null,
                                null,
                                java.util.Map.of("receiverEvidenceBlock", true)
                        ))
                )
        );

        assertEquals(1, result.requested());
        assertEquals(0, result.stored());
        assertEquals(1, result.skipped());
        assertEquals(0, result.matched());
        assertEquals(0, result.awaitingCarrier());
        verify(localEvidenceRepository).save(argThat(evidence ->
                "FAILED".equals(evidence.verificationStatus())
                        && evidence.verificationDetail().contains("native transport transcript missing")
        ));
        verifyNoInteractions(batchRepository, settlementRepository);
    }

    @Test
    void ingestLocalEvidenceRejectsAppGeneratedTransportCorrelationHash() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair receiverKeyPair = generator.generateKeyPair();
        String transportSessionHash = "e".repeat(64);
        String invalidSource = "APP_CORRELATION_HASH_V1";
        String canonicalPayload = """
                {"voucherId":"voucher-local-evidence-app-correlation","direction":"RECEIVE","deviceId":"receiver-device","senderDeviceId":"sender-device","receiverDeviceId":"receiver-device","prevHash":"prev-local-evidence-app-correlation","nonce":"nonce-local-evidence-app-correlation","deviceAttestationId":"%s","deviceAttestationVerdict":"%s","serverVerifiedTrustLevel":"%s","serverAttestationVerifiedAt":"%s","transportSessionHash":"%s","transportTranscriptSource":"%s","counter":16,"amount":"2.10000000"}
                """.formatted(DEVICE_ATTESTATION_ID, DEVICE_ATTESTATION_VERDICT, SERVER_VERIFIED_TRUST_LEVEL, SERVER_ATTESTATION_VERIFIED_AT, transportSessionHash, invalidSource).trim();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(receiverKeyPair.getPrivate());
        signer.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String receiverPublicKey = Base64.getEncoder().encodeToString(receiverKeyPair.getPublic().getEncoded());
        Device receiverDevice = new Device(
                "receiver-row-app-correlation",
                "receiver-device",
                1L,
                receiverPublicKey,
                1,
                DeviceStatus.ACTIVE,
                VERIFIED_DEVICE_METADATA,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));

        SettlementApplicationService.LocalEvidenceIngestResult result = service.ingestLocalEvidence(
                new SettlementApplicationService.LocalEvidenceIngestCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-local-evidence-app-correlation",
                        java.util.List.of(new SettlementApplicationService.LocalEvidenceSubmission(
                                "voucher-local-evidence-app-correlation",
                                "req-local-evidence-app-correlation",
                                "RECEIVE",
                                "sender-device",
                                "receiver-device",
                                new BigDecimal("2.10000000"),
                                16L,
                                "prev-local-evidence-app-correlation",
                                sha256Hex(canonicalPayload),
                                "nonce-local-evidence-app-correlation",
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
                                "1",
                                "1",
                                "SHA-256",
                                "SHA256withECDSA",
                                "device:receiver-device:v1",
                                sha256Hex(receiverPublicKey),
                                null,
                                DEVICE_ATTESTATION_ID,
                                DEVICE_ATTESTATION_VERDICT,
                                SERVER_VERIFIED_TRUST_LEVEL,
                                SERVER_ATTESTATION_VERIFIED_AT,
                                transportSessionHash,
                                invalidSource,
                                null,
                                null,
                                java.util.Map.of("receiverEvidenceBlock", true)
                        ))
                )
        );

        assertEquals(1, result.requested());
        assertEquals(0, result.stored());
        assertEquals(1, result.skipped());
        assertEquals(0, result.matched());
        assertEquals(0, result.awaitingCarrier());
        verify(localEvidenceRepository).save(argThat(evidence ->
                "FAILED".equals(evidence.verificationStatus())
                        && evidence.verificationDetail().contains("transport transcript source invalid")
        ));
        verifyNoInteractions(batchRepository, settlementRepository);
    }

    @Test
    void ingestLocalEvidenceRejectsMismatchedRegisteredPublicKeyFingerprint() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair receiverKeyPair = generator.generateKeyPair();
        String transportTranscript = "BLE:req-local-evidence-fingerprint:sender-device:receiver-device:2.10000000";
        String transportSessionHash = sha256Hex(transportTranscript);
        String canonicalPayload = """
                {"voucherId":"voucher-local-evidence-fingerprint","direction":"RECEIVE","deviceId":"receiver-device","senderDeviceId":"sender-device","receiverDeviceId":"receiver-device","prevHash":"prev-local-evidence-fingerprint","nonce":"nonce-local-evidence-fingerprint","deviceAttestationId":"%s","deviceAttestationVerdict":"%s","serverVerifiedTrustLevel":"%s","serverAttestationVerifiedAt":"%s","transportSessionHash":"%s","transportTranscriptSource":"%s","counter":15,"amount":"2.10000000"}
                """.formatted(DEVICE_ATTESTATION_ID, DEVICE_ATTESTATION_VERDICT, SERVER_VERIFIED_TRUST_LEVEL, SERVER_ATTESTATION_VERIFIED_AT, transportSessionHash, TRANSPORT_TRANSCRIPT_SOURCE).trim();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(receiverKeyPair.getPrivate());
        signer.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        Device receiverDevice = new Device(
                "receiver-row-fingerprint",
                "receiver-device",
                1L,
                Base64.getEncoder().encodeToString(receiverKeyPair.getPublic().getEncoded()),
                1,
                DeviceStatus.ACTIVE,
                VERIFIED_DEVICE_METADATA,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));

        SettlementApplicationService.LocalEvidenceIngestResult result = service.ingestLocalEvidence(
                new SettlementApplicationService.LocalEvidenceIngestCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-local-evidence-fingerprint",
                        java.util.List.of(new SettlementApplicationService.LocalEvidenceSubmission(
                                "voucher-local-evidence-fingerprint",
                                "req-local-evidence-fingerprint",
                                "RECEIVE",
                                "sender-device",
                                "receiver-device",
                                new BigDecimal("2.10000000"),
                                15L,
                                "prev-local-evidence-fingerprint",
                                sha256Hex(canonicalPayload),
                                "nonce-local-evidence-fingerprint",
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
                                "1",
                                "1",
                                "SHA-256",
                                "SHA256withECDSA",
                                "device:receiver-device:v1",
                                "wrong-fingerprint",
                                null,
                                DEVICE_ATTESTATION_ID,
                                DEVICE_ATTESTATION_VERDICT,
                                SERVER_VERIFIED_TRUST_LEVEL,
                                SERVER_ATTESTATION_VERIFIED_AT,
                                transportSessionHash,
                                TRANSPORT_TRANSCRIPT_SOURCE,
                                transportTranscript,
                                "UTF-8",
                                java.util.Map.of("receiverEvidenceBlock", true)
                        ))
                )
        );

        assertEquals(1, result.requested());
        assertEquals(0, result.stored());
        assertEquals(1, result.skipped());
        assertEquals(0, result.matched());
        assertEquals(0, result.awaitingCarrier());
        verify(localEvidenceRepository).save(argThat(evidence ->
                "FAILED".equals(evidence.verificationStatus())
                        && evidence.verificationDetail().contains("public key fingerprint mismatch")
        ));
    }

    @Test
    void verifiedReceiverLocalEvidenceWakesPendingSenderSettlementThroughEvidenceGate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair receiverKeyPair = generator.generateKeyPair();
        String transportTranscript = "BLE:req-local-evidence-wake:sender-device:receiver-device:2.10000000";
        String transportSessionHash = sha256Hex(transportTranscript);
        String canonicalPayload = """
                {"voucherId":"voucher-local-evidence-wake","direction":"RECEIVE","deviceId":"receiver-device","senderDeviceId":"sender-device","receiverDeviceId":"receiver-device","prevHash":"prev-local-evidence-wake","nonce":"nonce-local-evidence-wake","deviceAttestationId":"%s","deviceAttestationVerdict":"%s","serverVerifiedTrustLevel":"%s","serverAttestationVerifiedAt":"%s","transportSessionHash":"%s","transportTranscriptSource":"%s","counter":14,"amount":"2.10000000"}
                """.formatted(DEVICE_ATTESTATION_ID, DEVICE_ATTESTATION_VERDICT, SERVER_VERIFIED_TRUST_LEVEL, SERVER_ATTESTATION_VERIFIED_AT, transportSessionHash, TRANSPORT_TRANSCRIPT_SOURCE).trim();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(receiverKeyPair.getPrivate());
        signer.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String receiverPublicKey = Base64.getEncoder().encodeToString(receiverKeyPair.getPublic().getEncoded());
        Device receiverDevice = new Device(
                "receiver-row-wake",
                "receiver-device",
                1L,
                receiverPublicKey,
                1,
                DeviceStatus.ACTIVE,
                VERIFIED_DEVICE_METADATA,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-local-evidence-wake",
                "batch-local-evidence-wake",
                "voucher-local-evidence-wake",
                "collateral-local-evidence-wake",
                "sender-device",
                "receiver-device",
                1,
                1,
                14L,
                "nonce-sender-proof-wake",
                "hash-sender-proof-wake",
                "prev-sender-proof-wake",
                "signature-sender-proof-wake",
                new BigDecimal("2.10000000"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"senderLocalBlock\":true}",
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-local-evidence-wake",
                "batch-local-evidence-wake",
                "collateral-local-evidence-wake",
                "proof-local-evidence-wake",
                SettlementStatus.PENDING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-local-evidence-wake",
                77L,
                "sender-device",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-local-evidence-wake",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(receiverDevice));
        when(proofRepository.findByVoucherId("voucher-local-evidence-wake")).thenReturn(Optional.of(proof));
        when(settlementRepository.findLatestByProofId("proof-local-evidence-wake")).thenReturn(Optional.of(request));
        when(proofRepository.findById("proof-local-evidence-wake")).thenReturn(Optional.of(proof));
        when(collateralRepository.findById("collateral-local-evidence-wake")).thenReturn(Optional.of(collateral));

        SettlementApplicationService.LocalEvidenceIngestResult result = service.ingestLocalEvidence(
                new SettlementApplicationService.LocalEvidenceIngestCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "receiver-device",
                        "idempotency-local-evidence-receiver-wake",
                        java.util.List.of(new SettlementApplicationService.LocalEvidenceSubmission(
                                "voucher-local-evidence-wake",
                                "req-local-evidence-wake",
                                "RECEIVE",
                                "sender-device",
                                "receiver-device",
                                new BigDecimal("2.10000000"),
                                14L,
                                "prev-local-evidence-wake",
                                sha256Hex(canonicalPayload),
                                "nonce-local-evidence-wake",
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
                                "1",
                                "1",
                                "SHA-256",
                                "SHA256withECDSA",
                                "device:receiver-device:v1",
                                sha256Hex(receiverPublicKey),
                                null,
                                DEVICE_ATTESTATION_ID,
                                DEVICE_ATTESTATION_VERDICT,
                                SERVER_VERIFIED_TRUST_LEVEL,
                                SERVER_ATTESTATION_VERIFIED_AT,
                                transportSessionHash,
                                TRANSPORT_TRANSCRIPT_SOURCE,
                                transportTranscript,
                                "UTF-8",
                                java.util.Map.of("receiverEvidenceBlock", true)
                        ))
                )
        );

        assertEquals(1, result.stored());
        assertEquals(1, result.matched());
        assertEquals(0, result.awaitingCarrier());
        verify(localEvidenceRepository).markMatchingEvidence(proof);
        verify(localEvidenceRepository).markMatchingReceiverEvidence(proof);
        verify(proofRepository).updateLifecycle(
                eq("proof-local-evidence-wake"),
                eq(OfflineProofStatus.CONSUMED_PENDING_SETTLEMENT),
                isNull(),
                eq(true),
                eq(false),
                eq(false)
        );
        verify(settlementRepository).update(
                eq("settlement-local-evidence-wake"),
                eq(SettlementStatus.REJECTED),
                anyString(),
                eq(false),
                anyString()
        );
    }

    @Test
    void verifiedSenderLocalEvidenceAlsoWakesExistingCarrierWhenReceiverEvidenceAlreadyExists() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair senderKeyPair = generator.generateKeyPair();
        String transportTranscript = "BLE:req-local-evidence-sender-wake:sender-device:receiver-device:2.10000000";
        String transportSessionHash = sha256Hex(transportTranscript);
        String canonicalPayload = """
                {"voucherId":"voucher-local-evidence-sender-wake","direction":"SEND","deviceId":"sender-device","senderDeviceId":"sender-device","receiverDeviceId":"receiver-device","prevHash":"prev-local-evidence-sender-wake","nonce":"nonce-local-evidence-sender-wake","deviceAttestationId":"%s","deviceAttestationVerdict":"%s","serverVerifiedTrustLevel":"%s","serverAttestationVerifiedAt":"%s","transportSessionHash":"%s","transportTranscriptSource":"%s","counter":14,"amount":"2.10000000"}
                """.formatted(DEVICE_ATTESTATION_ID, DEVICE_ATTESTATION_VERDICT, SERVER_VERIFIED_TRUST_LEVEL, SERVER_ATTESTATION_VERIFIED_AT, transportSessionHash, TRANSPORT_TRANSCRIPT_SOURCE).trim();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(senderKeyPair.getPrivate());
        signer.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String senderPublicKey = Base64.getEncoder().encodeToString(senderKeyPair.getPublic().getEncoded());
        Device senderDevice = new Device(
                "sender-row-wake",
                "sender-device",
                1L,
                senderPublicKey,
                1,
                DeviceStatus.ACTIVE,
                VERIFIED_DEVICE_METADATA,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-local-evidence-sender-wake",
                "batch-local-evidence-sender-wake",
                "voucher-local-evidence-sender-wake",
                "collateral-local-evidence-sender-wake",
                "sender-device",
                "receiver-device",
                1,
                1,
                14L,
                "nonce-sender-proof-wake",
                "hash-sender-proof-wake",
                "prev-sender-proof-wake",
                "signature-sender-proof-wake",
                new BigDecimal("2.10000000"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"senderLocalBlock\":true}",
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "settlement-local-evidence-sender-wake",
                "batch-local-evidence-sender-wake",
                "collateral-local-evidence-sender-wake",
                "proof-local-evidence-sender-wake",
                SettlementStatus.PENDING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-local-evidence-sender-wake",
                77L,
                "sender-device",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-local-evidence-sender-wake",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(deviceRepository.findByDeviceId("sender-device")).thenReturn(Optional.of(senderDevice));
        when(proofRepository.findByVoucherId("voucher-local-evidence-sender-wake")).thenReturn(Optional.of(proof));
        when(settlementRepository.findLatestByProofId("proof-local-evidence-sender-wake")).thenReturn(Optional.of(request));
        when(proofRepository.findById("proof-local-evidence-sender-wake")).thenReturn(Optional.of(proof));
        when(collateralRepository.findById("collateral-local-evidence-sender-wake")).thenReturn(Optional.of(collateral));

        SettlementApplicationService.LocalEvidenceIngestResult result = service.ingestLocalEvidence(
                new SettlementApplicationService.LocalEvidenceIngestCommand(
                        SettlementApplicationService.UploaderType.SENDER,
                        "sender-device",
                        "idempotency-local-evidence-sender-wake",
                        java.util.List.of(new SettlementApplicationService.LocalEvidenceSubmission(
                                "voucher-local-evidence-sender-wake",
                                "req-local-evidence-sender-wake",
                                "SEND",
                                "sender-device",
                                "receiver-device",
                                new BigDecimal("2.10000000"),
                                14L,
                                "prev-local-evidence-sender-wake",
                                sha256Hex(canonicalPayload),
                                "nonce-local-evidence-sender-wake",
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
                                "1",
                                "1",
                                "SHA-256",
                                "SHA256withECDSA",
                                "device:sender-device:v1",
                                sha256Hex(senderPublicKey),
                                null,
                                DEVICE_ATTESTATION_ID,
                                DEVICE_ATTESTATION_VERDICT,
                                SERVER_VERIFIED_TRUST_LEVEL,
                                SERVER_ATTESTATION_VERIFIED_AT,
                                transportSessionHash,
                                TRANSPORT_TRANSCRIPT_SOURCE,
                                transportTranscript,
                                "UTF-8",
                                java.util.Map.of("senderLocalBlock", true)
                        ))
                )
        );

        assertEquals(1, result.stored());
        assertEquals(1, result.matched());
        assertEquals(0, result.awaitingCarrier());
        verify(localEvidenceRepository).markMatchingEvidence(proof);
        verify(localEvidenceRepository).markMatchingReceiverEvidence(proof);
        verify(proofRepository).updateLifecycle(
                eq("proof-local-evidence-sender-wake"),
                eq(OfflineProofStatus.CONSUMED_PENDING_SETTLEMENT),
                isNull(),
                eq(true),
                eq(false),
                eq(false)
        );
    }

    @Test
    void ingestLocalEvidenceSkipsMismatchedUploaderDevice() {
        SettlementApplicationService.LocalEvidenceIngestResult result = service.ingestLocalEvidence(
                new SettlementApplicationService.LocalEvidenceIngestCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "another-device",
                        "idempotency-local-evidence-skip",
                        java.util.List.of(new SettlementApplicationService.LocalEvidenceSubmission(
                                "voucher-local-evidence-skip",
                                "req-local-evidence-skip",
                                "RECEIVE",
                                "sender-device",
                                "receiver-device",
                                new BigDecimal("2.10000000"),
                                12L,
                                "prev-local-evidence",
                                "hash-local-evidence",
                                "nonce-local-evidence",
                                "signature-local-evidence",
                                "{\"voucherId\":\"voucher-local-evidence-skip\"}",
                                java.util.Map.of("receiverEvidenceBlock", true)
                        ))
                )
        );

        assertEquals(1, result.requested());
        assertEquals(0, result.stored());
        assertEquals(1, result.skipped());
        assertEquals(0, result.matched());
        assertEquals(0, result.awaitingCarrier());
        verifyNoInteractions(localEvidenceRepository, batchRepository, settlementRepository);
    }

    @Test
    void senderLocalBlockCanProceedWithoutReceiverEvidenceAfterSingleSideVerificationPolicy() {
        SettlementRequest request = new SettlementRequest(
                "settlement-sender-wait",
                "batch-sender-wait",
                "collateral-sender-wait",
                "proof-sender-wait",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementRequest pending = new SettlementRequest(
                "settlement-sender-wait",
                "batch-sender-wait",
                "collateral-sender-wait",
                "proof-sender-wait",
                SettlementStatus.PENDING,
                null,
                false,
                "{\"reasonCode\":\"RECEIVER_EVIDENCE_REQUIRED\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-sender-wait",
                77L,
                "sender-device",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-sender-wait",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        long now = System.currentTimeMillis();
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-sender-wait",
                "batch-sender-wait",
                "voucher-sender-wait",
                "collateral-sender-wait",
                "sender-device",
                "receiver-device",
                1,
                1,
                10L,
                "nonce-sender-wait",
                "hash-sender-wait",
                "GENESIS",
                "signature-sender-wait",
                new BigDecimal("6.00000000"),
                now,
                now + 60_000,
                "{}",
                "SENDER",
                "{\"requestId\":\"req-sender-wait\",\"senderLocalBlock\":true,\"senderLocalBlockVoucherId\":\"voucher-sender-wait\",\"senderLocalBlockAmount\":\"6.00000000\",\"senderLocalBlockSenderDeviceId\":\"sender-device\",\"senderLocalBlockReceiverDeviceId\":\"receiver-device\",\"senderLocalBlockCounter\":10,\"senderLocalBlockPrevHash\":\"GENESIS\",\"senderLocalBlockNewHash\":\"hash-sender-wait\",\"senderLocalBlockNonce\":\"nonce-sender-wait\",\"senderLocalBlockSignature\":\"signature-sender-wait\"}",
                OffsetDateTime.now()
        );
        when(settlementRepository.findById("settlement-sender-wait"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(pending));
        when(proofRepository.findById("proof-sender-wait")).thenReturn(Optional.of(proof));
        when(collateralRepository.findById("collateral-sender-wait")).thenReturn(Optional.of(collateral));

        SettlementRequest result = service.finalizeSettlement("settlement-sender-wait");

        assertEquals(SettlementStatus.PENDING, result.status());
        verify(settlementRepository, never()).update(
                eq("settlement-sender-wait"),
                eq(SettlementStatus.PENDING),
                isNull(),
                eq(false),
                argThat(payload -> payload.contains("RECEIVER_EVIDENCE_REQUIRED"))
        );
        verify(offlineSagaService, never()).markProcessing(
                eq(OfflineSagaType.SETTLEMENT),
                eq("settlement-sender-wait"),
                eq("AWAITING_RECEIVER_EVIDENCE"),
                any()
        );
        verify(proofRepository, atLeastOnce()).updateLifecycle(
                anyString(),
                any(OfflineProofStatus.class),
                any(),
                anyBoolean(),
                anyBoolean(),
                anyBoolean()
        );
        verify(collateralRepository, never()).deductLockedAndRemainingAmount(anyString(), any());
        verify(eventBus, never()).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void reconcileDirectLocalEvidenceCreatesSettlementCarrierFromVerifiedSenderEvidence() {
        long now = System.currentTimeMillis();
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("collateralId", "11111111-1111-1111-1111-111111111111");
        payload.put("keyVersion", 1);
        payload.put("policyVersion", 1);
        payload.put("timestampMs", now);
        payload.put("expiresAtMs", now + 600_000);
        payload.put("senderLocalBlock", true);
        payload.put("senderLocalBlockVoucherId", "voucher-direct-reconcile");
        payload.put("senderLocalBlockAmount", "3.30000000");
        payload.put("senderLocalBlockSenderDeviceId", "sender-device");
        payload.put("senderLocalBlockReceiverDeviceId", "receiver-device");
        payload.put("senderLocalBlockCounter", 31L);
        payload.put("senderLocalBlockPrevHash", "GENESIS");
        payload.put("senderLocalBlockNewHash", "hash-direct-reconcile");
        payload.put("senderLocalBlockNonce", "nonce-direct-reconcile");
        payload.put("senderLocalBlockSignature", "signature-direct-reconcile");

        OfflinePayLocalEvidence evidence = new OfflinePayLocalEvidence(
                null,
                "voucher-direct-reconcile",
                "req-direct-reconcile",
                "SEND",
                "SENDER",
                "sender-device",
                "sender-device",
                "receiver-device",
                new BigDecimal("3.30000000"),
                31L,
                "GENESIS",
                "hash-direct-reconcile",
                "nonce-direct-reconcile",
                "signature-direct-reconcile",
                "{\"voucherId\":\"voucher-direct-reconcile\"}",
                payload,
                "VERIFIED",
                "direct sender evidence verified",
                null
        );
        SettlementBatch batch = new SettlementBatch(
                "22222222-2222-2222-2222-222222222222",
                "sender-device",
                "direct-local-evidence:voucher-direct-reconcile",
                SettlementBatchStatus.CREATED,
                null,
                1,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementBatch uploadedBatch = new SettlementBatch(
                batch.id(),
                batch.sourceDeviceId(),
                batch.idempotencyKey(),
                SettlementBatchStatus.UPLOADED,
                null,
                1,
                "{}",
                batch.createdAt(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "11111111-1111-1111-1111-111111111111",
                77L,
                "sender-device",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-direct-reconcile",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "33333333-3333-3333-3333-333333333333",
                batch.id(),
                "voucher-direct-reconcile",
                collateral.id(),
                "sender-device",
                "receiver-device",
                77L,
                88L,
                1,
                1,
                31L,
                "nonce-direct-reconcile",
                "hash-direct-reconcile",
                "GENESIS",
                "signature-direct-reconcile",
                new BigDecimal("3.30000000"),
                now,
                now + 600_000,
                "{\"voucherId\":\"voucher-direct-reconcile\"}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "44444444-4444-4444-4444-444444444444",
                batch.id(),
                collateral.id(),
                proof.id(),
                SettlementStatus.PENDING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(localEvidenceRepository.findVerifiedSenderEvidenceAwaitingCarrier(25))
                .thenReturn(java.util.List.of(evidence));
        when(batchRepository.findByIdempotencyKey("direct-local-evidence:voucher-direct-reconcile"))
                .thenReturn(Optional.empty());
        when(batchRepository.save(
                eq("sender-device"),
                eq("direct-local-evidence:voucher-direct-reconcile"),
                eq(SettlementBatchStatus.CREATED),
                isNull(),
                eq(1),
                anyString()
        )).thenReturn(batch);
        when(batchRepository.findById(batch.id())).thenReturn(Optional.of(uploadedBatch));
        when(proofRepository.findByVoucherId("voucher-direct-reconcile"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(proof));
        when(proofRepository.findBySenderNonce("sender-device", "nonce-direct-reconcile")).thenReturn(Optional.empty());
        when(collateralRepository.findById(collateral.id())).thenReturn(Optional.of(collateral));
        when(deviceRepository.findByDeviceId("receiver-device")).thenReturn(Optional.of(new Device(
                "row-receiver-direct-reconcile",
                "receiver-device",
                88L,
                "receiver-public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));
        when(proofRepository.save(
                eq(batch.id()),
                eq("voucher-direct-reconcile"),
                eq(collateral.id()),
                eq("sender-device"),
                eq("receiver-device"),
                eq(collateral.userId()),
                anyLong(),
                eq(1),
                eq(1),
                eq(31L),
                eq("nonce-direct-reconcile"),
                eq("hash-direct-reconcile"),
                eq("GENESIS"),
                eq("signature-direct-reconcile"),
                eq(new BigDecimal("3.30000000")),
                eq(now),
                eq(now + 600_000),
                eq("{\"voucherId\":\"voucher-direct-reconcile\"}"),
                eq("SENDER"),
                anyString(),
                anyString()
        )).thenReturn(proof);
        when(settlementRepository.save(
                eq(batch.id()),
                eq(collateral.id()),
                eq(proof.id()),
                eq(SettlementStatus.PENDING),
                isNull(),
                eq(false),
                anyString()
        )).thenReturn(request);
        when(proofRepository.findById(proof.id())).thenReturn(Optional.of(proof));
        when(settlementRepository.findLatestByProofId(proof.id())).thenReturn(Optional.of(request));

        SettlementApplicationService.DirectLocalEvidenceReconcileResult result =
                service.reconcileDirectLocalEvidence(25);

        assertEquals(1, result.candidates());
        assertEquals(1, result.created());
        assertEquals(0, result.reused());
        assertEquals(0, result.finalized());
        assertEquals(1, result.rejected());
        assertEquals(0, result.skipped());
        assertEquals(java.util.List.of(batch.id()), result.batchIds());
        assertEquals(java.util.List.of(request.id()), result.settlementIds());
        verify(localEvidenceRepository).markMatchingEvidence(proof);
        verify(settlementRepository).save(
                eq(batch.id()),
                eq(collateral.id()),
                eq(proof.id()),
                eq(SettlementStatus.PENDING),
                isNull(),
                eq(false),
                anyString()
        );
    }

    @Test
    void reconcileDirectLocalEvidenceReusesExistingSettlementCarrierInsteadOfCreatingDuplicateProof() {
        long now = System.currentTimeMillis();
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("collateralId", "11111111-1111-1111-1111-111111111111");
        payload.put("keyVersion", 1);
        payload.put("policyVersion", 1);
        payload.put("timestampMs", now);
        payload.put("expiresAtMs", now + 600_000);
        payload.put("senderLocalBlock", true);
        payload.put("senderLocalBlockVoucherId", "voucher-existing-carrier");
        payload.put("senderLocalBlockAmount", "3.30000000");
        payload.put("senderLocalBlockSenderDeviceId", "sender-device");
        payload.put("senderLocalBlockReceiverDeviceId", "receiver-device");
        payload.put("senderLocalBlockCounter", 31L);
        payload.put("senderLocalBlockPrevHash", "GENESIS");
        payload.put("senderLocalBlockNewHash", "hash-existing-carrier");
        payload.put("senderLocalBlockNonce", "nonce-existing-carrier");
        payload.put("senderLocalBlockSignature", "signature-existing-carrier");

        OfflinePayLocalEvidence evidence = new OfflinePayLocalEvidence(
                null,
                "voucher-existing-carrier",
                "req-existing-carrier",
                "SEND",
                "SENDER",
                "sender-device",
                "sender-device",
                "receiver-device",
                new BigDecimal("3.30000000"),
                31L,
                "GENESIS",
                "hash-existing-carrier",
                "nonce-existing-carrier",
                "signature-existing-carrier",
                "{\"voucherId\":\"voucher-existing-carrier\"}",
                payload,
                "VERIFIED",
                "direct sender evidence verified",
                null
        );
        CollateralLock collateral = new CollateralLock(
                "11111111-1111-1111-1111-111111111111",
                77L,
                "sender-device",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("100"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-existing-carrier",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "33333333-3333-3333-3333-333333333333",
                "22222222-2222-2222-2222-222222222222",
                "voucher-existing-carrier",
                collateral.id(),
                "sender-device",
                "receiver-device",
                1,
                1,
                31L,
                "nonce-existing-carrier",
                "hash-existing-carrier",
                "GENESIS",
                "signature-existing-carrier",
                new BigDecimal("3.30000000"),
                now,
                now + 600_000,
                "{\"senderLocalBlock\":true,\"voucherId\":\"voucher-existing-carrier\"}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );
        SettlementRequest request = new SettlementRequest(
                "44444444-4444-4444-4444-444444444444",
                proof.batchId(),
                collateral.id(),
                proof.id(),
                SettlementStatus.PENDING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(localEvidenceRepository.findVerifiedSenderEvidenceAwaitingCarrier(25))
                .thenReturn(java.util.List.of(evidence));
        when(proofRepository.findByVoucherId("voucher-existing-carrier"))
                .thenReturn(Optional.of(proof));
        when(settlementRepository.findLatestByProofId(proof.id()))
                .thenReturn(Optional.of(request));
        when(proofRepository.findById(proof.id())).thenReturn(Optional.of(proof));
        when(collateralRepository.findById(collateral.id())).thenReturn(Optional.of(collateral));

        SettlementApplicationService.DirectLocalEvidenceReconcileResult result =
                service.reconcileDirectLocalEvidence(25);

        assertEquals(1, result.candidates());
        assertEquals(0, result.created());
        assertEquals(1, result.reused());
        assertEquals(0, result.skipped());
        assertEquals(java.util.List.of(), result.batchIds());
        assertEquals(java.util.List.of(request.id()), result.settlementIds());
        verify(localEvidenceRepository).markMatchingEvidence(proof);
        verify(batchRepository, never()).save(anyString(), anyString(), any(), any(), anyInt(), anyString());
        verify(proofRepository, never()).save(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void getLocalEvidenceStatusReturnsAwaitingCarrierSummaryByVoucherId() {
        when(localEvidenceRepository.summarizeStatus(eq("voucher-status"), isNull(), any()))
                .thenReturn(new OfflinePayLocalEvidenceRepository.LocalEvidenceStatus(
                        "voucher-status",
                        "req-status",
                        2,
                        2,
                        1,
                        1,
                        0,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        1,
                        "2026-06-12 02:00:00+00",
                        "2026-06-12 03:00:00+00"
                ));

        SettlementApplicationService.LocalEvidenceStatusResult result =
                service.getLocalEvidenceStatus("voucher-status", null);

        assertEquals("voucher-status", result.voucherId());
        assertEquals("req-status", result.sessionId());
        assertEquals(2, result.stored());
        assertEquals(1, result.matched());
        assertEquals(1, result.awaitingCarrier());
        assertEquals(0, result.failed());
        assertEquals("AWAITING_CARRIER", result.state());
        assertEquals(1, result.staleAwaitingCarrier());
        assertEquals(24, result.staleAfterHours());
        assertEquals("2026-06-12 02:00:00+00", result.oldestAwaitingCarrierAt());
    }

    @Test
    void getLocalEvidenceStatusReturnsGlobalOperationalSummaryWithoutIdentityFilter() {
        when(localEvidenceRepository.summarizeStatus(isNull(), isNull(), any()))
                .thenReturn(new OfflinePayLocalEvidenceRepository.LocalEvidenceStatus(
                        null,
                        null,
                        3,
                        3,
                        1,
                        2,
                        0,
                        2,
                        1,
                        1,
                        0,
                        0,
                        0,
                        2,
                        "2026-06-12 01:00:00+00",
                        "2026-06-12 03:00:00+00"
                ));

        SettlementApplicationService.LocalEvidenceStatusResult result =
                service.getLocalEvidenceStatus(" ", null, 6);

        assertEquals(3, result.total());
        assertEquals(2, result.awaitingCarrier());
        assertEquals(2, result.staleAwaitingCarrier());
        assertEquals(6, result.staleAfterHours());
        assertEquals("AWAITING_CARRIER", result.state());
    }

    @Test
    void submitBatchRejectsDuplicateSenderOfflineTxSequenceBeforeCreatingBatch() {
        long now = System.currentTimeMillis();
        OfflinePaymentProof existingProof = new OfflinePaymentProof(
                "proof-existing-sequence-submit",
                "batch-existing-sequence-submit",
                "voucher-existing-sequence-submit",
                "collateral-existing-sequence-submit",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-existing-sequence-submit",
                "hash-existing",
                "GENESIS",
                "signature-existing",
                new BigDecimal("1"),
                now,
                now + 60_000,
                "{}",
                "SENDER",
                "{\"requestId\":\"request-existing\",\"offlineTxSequence\":12}",
                OffsetDateTime.now()
        );
        SettlementApplicationService.ProofSubmission submission = new SettlementApplicationService.ProofSubmission(
                "voucher-new-sequence-submit",
                "collateral-new-sequence-submit",
                "device-1",
                "device-2",
                1,
                1,
                2L,
                "nonce-new-sequence-submit",
                "hash-new",
                "GENESIS",
                "signature-new",
                new BigDecimal("1"),
                now,
                now + 60_000,
                "{}",
                java.util.Map.of("requestId", "request-new-sequence-submit", "offlineTxSequence", 12)
        );
        when(proofRepository.findBySenderOfflineTxSequence("device-1", 12L))
                .thenReturn(Optional.of(existingProof));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitBatch(new SettlementApplicationService.SubmitSettlementBatchCommand(
                        SettlementApplicationService.UploaderType.RECEIVER,
                        "device-2",
                        "idempotency-new-sequence-submit",
                        java.util.List.of(submission),
                        "MANUAL"
                ))
        );

        assertTrue(exception.getMessage().contains("duplicate offline proof submission by offlineTxSequence"));
        verify(batchRepository, never()).save(anyString(), anyString(), any(), any(), anyInt(), anyString());
        verify(proofRepository, never()).save(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
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
        long proofTimestamp = System.currentTimeMillis();
        long proofExpiresAt = proofTimestamp + 60_000;
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-1",
                "batch-1",
                "voucher-1",
                "collateral-1",
                "device-1",
                "app-suffix:e7eaeaa7",
                77L,
                88L,
                1,
                1,
                1L,
                "nonce-1",
                proofHash,
                "GENESIS",
                "local_sig_settled",
                new BigDecimal("100"),
                proofTimestamp,
                proofExpiresAt,
                "{\"voucherId\":\"voucher-1\",\"counterpartyDeviceId\":\"app-suffix:e7eaeaa7\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-1\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"app-suffix:e7eaeaa7\",\"amount\":\"100\",\"expiresAt\":\""
                        + proofExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"100\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-1\",\"newStateHash\":\""
                        + proofHash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_settled\",\"timestamp\":\""
                        + proofTimestamp
                        + "\"}}",
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
        Device receiverDevice = new Device(
                "row-2",
                "98db6beb-4ae1-4027-b9ee-507ce7eaeaa7",
                88L,
                "receiver-public-key",
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
        when(deviceRepository.findByDeviceId("app-suffix:e7eaeaa7")).thenReturn(Optional.empty());
        when(deviceRepository.findUniqueActiveByDeviceIdSuffix("e7eaeaa7")).thenReturn(Optional.of(receiverDevice));
        when(settlementResultRepository.existsByVoucherIdExcludingSettlementId("voucher-1", "settlement-1"))
                .thenReturn(false);

        SettlementRequest result = service.finalizeSettlement("settlement-1");

        verify(settlementResultRepository).existsByVoucherIdExcludingSettlementId("voucher-1", "settlement-1");
        verify(eventBus).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                eq("settlement-1"),
                eq("batch-1"),
                eq("proof-1"),
                argThat(payload -> payload.contains("\"senderDeviceSyncCommand\"")
                        && payload.contains("\"receiverDeviceSyncCommand\"")
                        && payload.contains("\"deviceId\":\"device-1\"")
                        && payload.contains("\"deviceId\":\"98db6beb-4ae1-4027-b9ee-507ce7eaeaa7\"")),
                anyString()
        );
        verify(settlementRepository).update(anyString(), any(SettlementStatus.class), any(), anyBoolean(), anyString());
        verify(proofRepository).ensureReceivedUnsettledAmount("proof-1", new BigDecimal("100.000000"));
        verify(issuedProofVerificationService, never()).markConsumed(any());
        assertEquals(SettlementStatus.SETTLED, result.status());
    }

    @Test
    void settledProofCanSpendAcrossUserAssetCollateralScope() {
        SettlementRequest request = new SettlementRequest(
                "settlement-aggregate",
                "batch-aggregate",
                "collateral-primary",
                "proof-aggregate",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock primaryCollateral = new CollateralLock(
                "collateral-primary",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("80"),
                new BigDecimal("80"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-primary",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock secondaryCollateral = new CollateralLock(
                "collateral-secondary",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("50"),
                new BigDecimal("50"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-secondary",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock aggregateCollateral = new CollateralLock(
                "collateral-primary",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("130"),
                new BigDecimal("130"),
                "AGGREGATED",
                1,
                CollateralStatus.LOCKED,
                "lock-primary,lock-secondary",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementRequest settled = new SettlementRequest(
                "settlement-aggregate",
                "batch-aggregate",
                "collateral-primary",
                "proof-aggregate",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{\"releaseAction\":\"RELEASE\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String proofHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("120"),
                1L,
                "device-1",
                "nonce-aggregate"
        );
        long proofTimestamp = System.currentTimeMillis();
        long proofExpiresAt = proofTimestamp + 60_000;
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-aggregate",
                "batch-aggregate",
                "voucher-aggregate",
                "collateral-primary",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-aggregate",
                proofHash,
                "GENESIS",
                "local_sig_aggregate",
                new BigDecimal("120"),
                proofTimestamp,
                proofExpiresAt,
                "{\"voucherId\":\"voucher-aggregate\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-aggregate\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"120\",\"expiresAt\":\""
                        + proofExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"130\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"120\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-aggregate\",\"newStateHash\":\""
                        + proofHash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_aggregate\",\"timestamp\":\""
                        + proofTimestamp
                        + "\"}}",
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

        when(settlementRepository.findById("settlement-aggregate"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(settled));
        when(collateralRepository.findById("collateral-primary")).thenReturn(Optional.of(primaryCollateral));
        when(collateralRepository.findAggregateByUserIdAndAssetCode(77L, "USDT"))
                .thenReturn(Optional.of(aggregateCollateral));
        when(collateralRepository.findActiveByUserIdAndAssetCode(77L, "USDT"))
                .thenReturn(java.util.List.of(primaryCollateral, secondaryCollateral));
        when(proofRepository.findById("proof-aggregate")).thenReturn(Optional.of(proof));
        when(proofRepository.findByCollateralId("collateral-primary")).thenReturn(java.util.List.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-aggregate")).thenReturn(false);
        when(issuedProofVerificationService.verify(any())).thenReturn(
                IssuedProofVerificationService.VerificationResult.valid(
                        new IssuedOfflineProof(
                                "issued-proof-aggregate",
                                77L,
                                "device-1",
                                "collateral-primary",
                                "USDT",
                                new BigDecimal("130"),
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

        SettlementRequest result = service.finalizeSettlement("settlement-aggregate");

        assertEquals(SettlementStatus.SETTLED, result.status());
        verify(collateralRepository).deductLockedAndRemainingAmount(
                eq("collateral-primary"),
                argThat(amount -> amount.compareTo(new BigDecimal("80")) == 0)
        );
        verify(collateralRepository).deductLockedAndRemainingAmount(
                eq("collateral-secondary"),
                argThat(amount -> amount.compareTo(new BigDecimal("40")) == 0)
        );
    }

    @Test
    void registeredDeviceCanSettleAgainstUserAssetCollateralMovedToAnotherDevice() {
        SettlementRequest request = new SettlementRequest(
                "settlement-rebound-device",
                "batch-rebound-device",
                "collateral-rebound",
                "proof-rebound-device",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateralOnPreviousDevice = new CollateralLock(
                "collateral-rebound",
                77L,
                "previous-device",
                "USDT",
                new BigDecimal("11.7"),
                new BigDecimal("11.7"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-rebound",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock aggregateCollateral = new CollateralLock(
                "collateral-rebound",
                77L,
                "AGGREGATED",
                "USDT",
                new BigDecimal("11.7"),
                new BigDecimal("11.7"),
                "AGGREGATED",
                1,
                CollateralStatus.LOCKED,
                "lock-rebound",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        SettlementRequest settled = new SettlementRequest(
                "settlement-rebound-device",
                "batch-rebound-device",
                "collateral-rebound",
                "proof-rebound-device",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{\"releaseAction\":\"RELEASE\"}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String proofHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("5"),
                1L,
                "current-device",
                "nonce-rebound-device"
        );
        long proofTimestamp = System.currentTimeMillis();
        long proofExpiresAt = proofTimestamp + 60_000;
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-rebound-device",
                "batch-rebound-device",
                "voucher-rebound-device",
                "collateral-rebound",
                "current-device",
                "receiver-device",
                1,
                1,
                1L,
                "nonce-rebound-device",
                proofHash,
                "GENESIS",
                "local_sig_rebound",
                new BigDecimal("5"),
                proofTimestamp,
                proofExpiresAt,
                "{\"voucherId\":\"voucher-rebound-device\",\"counterpartyDeviceId\":\"receiver-device\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-rebound-device\",\"deviceId\":\"current-device\",\"counterpartyDeviceId\":\"receiver-device\",\"amount\":\"5\",\"expiresAt\":\""
                        + proofExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"11.7\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"current-device\",\"amount\":\"5\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-rebound-device\",\"newStateHash\":\""
                        + proofHash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_rebound\",\"timestamp\":\""
                        + proofTimestamp
                        + "\"}}",
                OffsetDateTime.now()
        );
        Device device = new Device(
                "row-current-device",
                "current-device",
                77L,
                "public-key",
                1,
                DeviceStatus.ACTIVE,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(settlementRepository.findById("settlement-rebound-device"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(settled));
        when(collateralRepository.findById("collateral-rebound")).thenReturn(Optional.of(collateralOnPreviousDevice));
        when(collateralRepository.findAggregateByUserIdAndAssetCode(77L, "USDT"))
                .thenReturn(Optional.of(aggregateCollateral));
        when(collateralRepository.findActiveByUserIdAndAssetCode(77L, "USDT"))
                .thenReturn(java.util.List.of(collateralOnPreviousDevice));
        when(proofRepository.findById("proof-rebound-device")).thenReturn(Optional.of(proof));
        when(proofRepository.findByCollateralId("collateral-rebound")).thenReturn(java.util.List.of(proof));
        when(deviceRepository.findByDeviceId("current-device")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-rebound-device")).thenReturn(false);
        when(issuedProofVerificationService.verify(any())).thenReturn(
                IssuedProofVerificationService.VerificationResult.valid(
                        new IssuedOfflineProof(
                                "issued-proof-rebound-device",
                                77L,
                                "current-device",
                                "collateral-rebound",
                                "USDT",
                                new BigDecimal("11.7"),
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

        SettlementRequest result = service.finalizeSettlement("settlement-rebound-device");

        assertEquals(SettlementStatus.SETTLED, result.status());
        verify(collateralRepository).deductLockedAndRemainingAmount(
                eq("collateral-rebound"),
                argThat(amount -> amount.compareTo(new BigDecimal("5")) == 0)
        );
    }

    @Test
    void settledProofCanContinueDeviceAssetChainAcrossNewCollateralRows() {
        SettlementRequest request = new SettlementRequest(
                "settlement-cross-chain",
                "batch-cross-chain",
                "collateral-new",
                "proof-cross-chain",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock primaryCollateral = new CollateralLock(
                "collateral-new",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("6"),
                new BigDecimal("6"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-new",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock aggregateCollateral = new CollateralLock(
                "collateral-new",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("6"),
                new BigDecimal("6"),
                "AGGREGATED",
                1,
                CollateralStatus.LOCKED,
                "lock-new",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        String previousHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("1"),
                20L,
                "device-1",
                "nonce-previous"
        );
        String incomingHash = spendingProofHashService.computeNewStateHash(
                previousHash,
                new BigDecimal("1"),
                21L,
                "device-1",
                "nonce-current"
        );
        long proofTimestamp = System.currentTimeMillis();
        long proofExpiresAt = proofTimestamp + 60_000;
        OfflinePaymentProof previousProof = new OfflinePaymentProof(
                "proof-previous",
                "batch-previous",
                "voucher-previous",
                "collateral-old",
                "device-1",
                "device-2",
                1,
                1,
                20L,
                "nonce-previous",
                previousHash,
                "GENESIS",
                "local_sig_previous",
                new BigDecimal("1"),
                proofTimestamp - 1_000,
                proofExpiresAt,
                "{\"voucherId\":\"voucher-previous\"}",
                "SENDER",
                "{}",
                OffsetDateTime.now()
        );
        OfflinePaymentProof incomingProof = new OfflinePaymentProof(
                "proof-cross-chain",
                "batch-cross-chain",
                "voucher-cross-chain",
                "collateral-new",
                "device-1",
                "device-2",
                1,
                1,
                21L,
                "nonce-current",
                incomingHash,
                previousHash,
                "local_sig_current",
                new BigDecimal("1"),
                proofTimestamp,
                proofExpiresAt,
                "{\"voucherId\":\"voucher-cross-chain\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-cross-chain\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"1\",\"expiresAt\":\""
                        + proofExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"6\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"1\",\"monotonicCounter\":\"21\",\"nonce\":\"nonce-current\",\"newStateHash\":\""
                        + incomingHash
                        + "\",\"prevStateHash\":\""
                        + previousHash
                        + "\",\"signature\":\"local_sig_current\",\"timestamp\":\""
                        + proofTimestamp
                        + "\"}}",
                OffsetDateTime.now()
        );
        SettlementRequest settled = new SettlementRequest(
                "settlement-cross-chain",
                "batch-cross-chain",
                "collateral-new",
                "proof-cross-chain",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{\"releaseAction\":\"RELEASE\"}",
                OffsetDateTime.now(),
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

        when(settlementRepository.findById("settlement-cross-chain"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(settled));
        when(collateralRepository.findById("collateral-new")).thenReturn(Optional.of(primaryCollateral));
        when(collateralRepository.findAggregateByUserIdAndAssetCode(77L, "USDT"))
                .thenReturn(Optional.of(aggregateCollateral));
        when(collateralRepository.findActiveByUserIdAndAssetCode(77L, "USDT"))
                .thenReturn(java.util.List.of(primaryCollateral));
        when(proofRepository.findById("proof-cross-chain")).thenReturn(Optional.of(incomingProof));
        when(proofRepository.findBySenderDeviceUserAndAsset("device-1", 77L, "USDT"))
                .thenReturn(java.util.List.of(previousProof, incomingProof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));
        when(settlementResultRepository.existsByVoucherId("voucher-cross-chain")).thenReturn(false);

        SettlementRequest result = service.finalizeSettlement("settlement-cross-chain");

        assertEquals(SettlementStatus.SETTLED, result.status());
        verify(collateralRepository).deductLockedAndRemainingAmount(
                eq("collateral-new"),
                argThat(amount -> amount.compareTo(new BigDecimal("1")) == 0)
        );
    }

    @Test
    void getSettlementDetailIncludesSagaAndReconciliationState() {
        SettlementRequest request = new SettlementRequest(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                SettlementStatus.SETTLED,
                "SETTLED",
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        OfflineSaga saga = new OfflineSaga(
                "saga-1",
                OfflineSagaType.SETTLEMENT,
                "settlement-1",
                OfflineSagaStatus.COMPENSATION_REQUIRED,
                "COMPENSATION_REQUIRED",
                "HISTORY_SYNC_FAIL",
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        ReconciliationCase reconciliationCase = new ReconciliationCase(
                "case-1",
                "settlement-1",
                "batch-1",
                "proof-1",
                "voucher-1",
                "HISTORY_SYNC_FAILED",
                ReconciliationCaseStatus.OPEN,
                "HISTORY_SYNC_FAIL",
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );

        when(settlementRepository.findById("settlement-1")).thenReturn(Optional.of(request));
        when(offlineSagaRepository.findBySagaTypeAndReferenceId(OfflineSagaType.SETTLEMENT, "settlement-1"))
                .thenReturn(Optional.of(saga));
        when(reconciliationCaseRepository.findLatestOpenBySettlementId("settlement-1"))
                .thenReturn(Optional.of(reconciliationCase));
        when(proofRepository.findById("proof-1")).thenReturn(Optional.empty());
        when(collateralRepository.findById("collateral-1")).thenReturn(Optional.empty());

        SettlementApplicationService.SettlementDetailView detail = service.getSettlementDetail("settlement-1");

        assertEquals("settlement-1", detail.settlementRequest().id());
        assertEquals(OfflineSagaStatus.COMPENSATION_REQUIRED, detail.settlementSaga().status());
        assertEquals("HISTORY_SYNC_FAILED", detail.reconciliationCase().caseType());
    }

    @Test
    void finalizeSettlementRejectCreatesCollateralReleaseInsteadOfLedgerSync() {
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
        String failedProofHash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("10"),
                1L,
                "device-1",
                "nonce-1"
        );
        long failedProofTimestamp = System.currentTimeMillis();
        long failedProofExpiresAt = failedProofTimestamp + 60_000;
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
                failedProofHash,
                "GENESIS",
                "local_sig_failed",
                new BigDecimal("10"),
                failedProofTimestamp,
                failedProofExpiresAt,
                "{\"voucherId\":\"voucher-1\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-1\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"10\",\"expiresAt\":\""
                        + failedProofExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"10\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-1\",\"newStateHash\":\""
                        + failedProofHash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_failed\",\"timestamp\":\""
                        + failedProofTimestamp
                        + "\"}}",
                OffsetDateTime.now()
        );
        Device inactiveDevice = new Device(
                "device-row-1",
                "device-1",
                77L,
                "public-key",
                1,
                DeviceStatus.FROZEN,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(settlementRepository.findById("settlement-1")).thenReturn(
                Optional.of(request),
                Optional.of(new SettlementRequest(
                        "settlement-1",
                        "batch-1",
                        "collateral-1",
                        "proof-1",
                        SettlementStatus.REJECTED,
                        "DEVICE_NOT_ACTIVE",
                        false,
                        "{}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                ))
        );
        when(proofRepository.findById("proof-1")).thenReturn(Optional.of(proof));
        when(collateralRepository.findById("collateral-1")).thenReturn(Optional.of(collateral));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(inactiveDevice));

        service.finalizeSettlement("settlement-1");

        verify(eventBus, never()).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(collateralOperationRepository, never()).saveRequested(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                eq(CollateralOperationType.RELEASE),
                any(),
                anyString(),
                anyString()
        );
        verify(eventBus, never()).publishCollateralOperationRequested(
                anyString(),
                eq("RELEASE"),
                anyString(),
                anyString(),
                anyString()
        );
        verify(offlineSagaService).markFailed(
                eq(OfflineSagaType.SETTLEMENT),
                eq("settlement-1"),
                eq("SETTLEMENT_REJECTED"),
                eq("DEVICE_NOT_ACTIVE"),
                any()
        );
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
                        + "\"},\"deviceRegistrationId\":\"other-row\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\"}",
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
        verify(proofRepository, never()).ensureReceivedUnsettledAmount(anyString(), any());
        verify(collateralRepository, never()).deductLockedAndRemainingAmount(anyString(), any());
        verify(eventBus, never()).publishExternalSyncRequested(
                eq("LEDGER_SYNC_REQUESTED"),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
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
    void finalizeSettlementCreatesOverspendReconciliationCaseWhenLocalAvailableExceeded() {
        SettlementRequest request = new SettlementRequest(
                "settlement-overspend",
                "batch-overspend",
                "collateral-overspend",
                "proof-overspend",
                SettlementStatus.VALIDATING,
                null,
                false,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CollateralLock collateral = new CollateralLock(
                "collateral-overspend",
                77L,
                "device-1",
                "USDT",
                new BigDecimal("150"),
                new BigDecimal("150"),
                "GENESIS",
                1,
                CollateralStatus.LOCKED,
                "lock-overspend",
                OffsetDateTime.now().plusDays(1),
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        long now = System.currentTimeMillis();
        long expiresAt = now + 60_000;
        String hash = spendingProofHashService.computeNewStateHash(
                "GENESIS",
                new BigDecimal("100"),
                1L,
                "device-1",
                "nonce-overspend"
        );
        String rawPayloadJson = "{\"voucherId\":\"voucher-overspend\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"100\",\"expiresAt\":\""
                + expiresAt
                + "\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"100\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-overspend\",\"newStateHash\":\""
                + hash
                + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_fake\",\"timestamp\":\""
                + now
                + "\"},\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"50\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\"}";
        OfflinePaymentProof proof = new OfflinePaymentProof(
                "proof-overspend",
                "batch-overspend",
                "voucher-overspend",
                "collateral-overspend",
                "device-1",
                "device-2",
                1,
                1,
                1L,
                "nonce-overspend",
                hash,
                "GENESIS",
                "local_sig_fake",
                new BigDecimal("100"),
                now,
                expiresAt,
                "{\"voucherId\":\"voucher-overspend\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                rawPayloadJson,
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

        when(settlementRepository.findById("settlement-overspend"))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(new SettlementRequest(
                        "settlement-overspend",
                        "batch-overspend",
                        "collateral-overspend",
                        "proof-overspend",
                        SettlementStatus.REJECTED,
                        "LOCAL_AVAILABLE_AMOUNT_EXCEEDED",
                        false,
                        "{\"reasonCode\":\"LOCAL_AVAILABLE_AMOUNT_EXCEEDED\"}",
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )));
        when(collateralRepository.findById("collateral-overspend")).thenReturn(Optional.of(collateral));
        when(proofRepository.findById("proof-overspend")).thenReturn(Optional.of(proof));
        when(deviceRepository.findByDeviceId("device-1")).thenReturn(Optional.of(device));

        SettlementRequest result = service.finalizeSettlement("settlement-overspend");

        assertEquals(SettlementStatus.REJECTED, result.status());
        assertTrue(result.settlementResultJson().contains("LOCAL_AVAILABLE_AMOUNT_EXCEEDED"));
        verify(reconciliationCaseRepository).save(
                eq("settlement-overspend"),
                eq("batch-overspend"),
                eq("proof-overspend"),
                eq("voucher-overspend"),
                eq("OVERSPEND_ATTEMPT"),
                eq(io.korion.offlinepay.domain.status.ReconciliationCaseStatus.OPEN),
                eq("LOCAL_AVAILABLE_AMOUNT_EXCEEDED"),
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
                "local_sig_ledger_fail",
                new BigDecimal("10"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":true,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\"}",
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
                        + "\"},\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\"}",
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
                "local_sig_ledger_fail",
                new BigDecimal("10"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"AUTO_WITHDRAW\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\"}",
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
                "local_sig_ledger_fail",
                new BigDecimal("10"),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000,
                "{}",
                "SENDER",
                "{\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"deviceRegistrationId\":\"row-1\",\"signedUserId\":77,\"authMethod\":\"PIN\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\"}",
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
    void recordBatchProcessingFailureKeepsBatchRetryable() {
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

        assertFalse(outcome.deadLettered());
        verify(reconciliationCaseRepository, never()).save(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
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
        long ledgerFailTimestamp = System.currentTimeMillis();
        long ledgerFailExpiresAt = ledgerFailTimestamp + 60_000;
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
                "local_sig_ledger_fail",
                new BigDecimal("100"),
                ledgerFailTimestamp,
                ledgerFailExpiresAt,
                "{\"voucherId\":\"voucher-ledger-fail\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-ledger-fail\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"100\",\"expiresAt\":\""
                        + ledgerFailExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"100\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-ledger-fail\",\"newStateHash\":\""
                        + proofHash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_ledger_fail\",\"timestamp\":\""
                        + ledgerFailTimestamp
                        + "\"}}",
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
        long historyFailTimestamp = System.currentTimeMillis();
        long historyFailExpiresAt = historyFailTimestamp + 60_000;
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
                "local_sig_history_fail",
                new BigDecimal("100"),
                historyFailTimestamp,
                historyFailExpiresAt,
                "{\"voucherId\":\"voucher-history-fail\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-history-fail\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"100\",\"expiresAt\":\""
                        + historyFailExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"100\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-history-fail\",\"newStateHash\":\""
                        + proofHash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_history_fail\",\"timestamp\":\""
                        + historyFailTimestamp
                        + "\"}}",
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
        long ledgerOpenTimestamp = System.currentTimeMillis();
        long ledgerOpenExpiresAt = ledgerOpenTimestamp + 60_000;
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
                "local_sig_ledger_open",
                new BigDecimal("100"),
                ledgerOpenTimestamp,
                ledgerOpenExpiresAt,
                "{\"voucherId\":\"voucher-ledger-open\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-ledger-open\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"100\",\"expiresAt\":\""
                        + ledgerOpenExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"100\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-ledger-open\",\"newStateHash\":\""
                        + proofHash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_ledger_open\",\"timestamp\":\""
                        + ledgerOpenTimestamp
                        + "\"}}",
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
        long historyOpenTimestamp = System.currentTimeMillis();
        long historyOpenExpiresAt = historyOpenTimestamp + 60_000;
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
                "local_sig_history_open",
                new BigDecimal("100"),
                historyOpenTimestamp,
                historyOpenExpiresAt,
                "{\"voucherId\":\"voucher-history-open\",\"counterpartyDeviceId\":\"device-2\"}",
                "SENDER",
                "{\"voucherId\":\"voucher-history-open\",\"deviceId\":\"device-1\",\"counterpartyDeviceId\":\"device-2\",\"amount\":\"100\",\"expiresAt\":\""
                        + historyOpenExpiresAt
                        + "\",\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\",\"uiMode\":\"SEND\",\"connectionType\":\"FAST_CONTACT\",\"paymentFlow\":\"FAST_PAYMENT\",\"senderAuthRequired\":true,\"ledgerExecutionMode\":\"INTERNAL_LEDGER_ONLY\",\"dualAmountEntered\":false,\"deviceTrustLevel\":\"HARDWARE_BACKED_VERIFIED\",\"deviceAttestationId\":\"attestation-001\",\"deviceAttestationVerdict\":\"HARDWARE_BACKED_VERIFIED\",\"serverVerifiedTrustLevel\":\"SERVER_VERIFIED\",\"serverAttestationVerifiedAt\":\"2026-06-11T23:58:00.000Z\",\"spendingProof\":{\"deviceId\":\"device-1\",\"amount\":\"100\",\"monotonicCounter\":\"1\",\"nonce\":\"nonce-history-open\",\"newStateHash\":\""
                        + proofHash
                        + "\",\"prevStateHash\":\"GENESIS\",\"signature\":\"local_sig_history_open\",\"timestamp\":\""
                        + historyOpenTimestamp
                        + "\"}}",
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

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
