package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.port.SettlementBatchRepository;
import io.korion.offlinepay.application.port.SettlementConflictRepository;
import io.korion.offlinepay.application.port.SettlementRepository;
import io.korion.offlinepay.application.port.SettlementResultRepository;
import io.korion.offlinepay.application.service.settlement.ProofChainValidator;
import io.korion.offlinepay.application.service.settlement.ProofConflictDetector;
import io.korion.offlinepay.application.service.settlement.ProofSchemaValidator;
import io.korion.offlinepay.application.service.settlement.ProofFingerprintService;
import io.korion.offlinepay.application.service.settlement.SpendingProofHashService;
import io.korion.offlinepay.application.service.settlement.SettlementPolicyEvaluator;
import io.korion.offlinepay.application.service.settlement.DeviceSignatureVerificationService;
import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.CollateralStatus;
import io.korion.offlinepay.domain.status.DeviceStatus;
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
    private final SettlementConflictRepository settlementConflictRepository = Mockito.mock(SettlementConflictRepository.class);
    private final SettlementBatchEventBus eventBus = Mockito.mock(SettlementBatchEventBus.class);
    private final CoinManageSettlementPort coinManageSettlementPort = Mockito.mock(CoinManageSettlementPort.class);
    private final FoxCoinHistoryPort foxCoinHistoryPort = Mockito.mock(FoxCoinHistoryPort.class);
    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final SettlementApplicationService service = new SettlementApplicationService(
            collateralRepository,
            deviceRepository,
            proofRepository,
            batchRepository,
            settlementRepository,
            settlementResultRepository,
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
            new ProofConflictDetector(jsonService),
            new ProofChainValidator(jsonService, spendingProofHashService),
            new SettlementPolicyEvaluator(jsonService),
            new DeviceSignatureVerificationService()
    );

    @Test
    void finalizeSettlementSyncsCoinManageLedgerAndFoxCoinHistory() {
        SettlementRequest request = new SettlementRequest(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                SettlementStatus.VALIDATING,
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
                "{\"network\":\"TRC-20\",\"token\":\"USDT\",\"availableAmount\":\"100\"}",
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
        verify(settlementRepository).update(anyString(), any(SettlementStatus.class), anyBoolean(), anyString());
        assertEquals(SettlementStatus.SETTLED, result.status());
    }
}
