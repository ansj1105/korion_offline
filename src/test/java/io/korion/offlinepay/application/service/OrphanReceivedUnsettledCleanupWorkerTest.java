package io.korion.offlinepay.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.korion.offlinepay.application.port.OfflinePaymentProofRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.OfflinePaymentProof;
import io.korion.offlinepay.domain.status.OfflineProofStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrphanReceivedUnsettledCleanupWorkerTest {

    @Test
    void closesOrphanReceivedUnsettledCandidates() {
        OfflinePaymentProofRepository proofRepository = Mockito.mock(OfflinePaymentProofRepository.class);
        OrphanReceivedUnsettledCleanupWorker worker = new OrphanReceivedUnsettledCleanupWorker(
                proofRepository,
                properties(true)
        );
        when(proofRepository.findOrphanReceivedUnsettledCandidates(any(), eq(25)))
                .thenReturn(List.of(proof("proof-1"), proof("proof-2")));

        worker.poll();

        verify(proofRepository).markReceivedCollateralSettled(
                eq(List.of("proof-1", "proof-2")),
                isNull(),
                startsWith("orphan-cleanup:")
        );
    }

    @Test
    void skipsCleanupWhenWorkerDisabled() {
        OfflinePaymentProofRepository proofRepository = Mockito.mock(OfflinePaymentProofRepository.class);
        OrphanReceivedUnsettledCleanupWorker worker = new OrphanReceivedUnsettledCleanupWorker(
                proofRepository,
                properties(false)
        );

        worker.poll();

        verify(proofRepository, never()).findOrphanReceivedUnsettledCandidates(any(), Mockito.anyInt());
        verify(proofRepository, never()).markReceivedCollateralSettled(Mockito.anyList(), Mockito.any(), Mockito.any());
    }

    private static OfflinePaymentProof proof(String id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new OfflinePaymentProof(
                id,
                "batch",
                "voucher",
                "collateral",
                "sender",
                "receiver",
                1,
                1,
                1,
                "nonce",
                "hash",
                "prev",
                "sig",
                BigDecimal.ONE,
                1L,
                2L,
                "{}",
                "SENDER",
                "BLE",
                OfflineProofStatus.SETTLED,
                null,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                null,
                null,
                null,
                "{}",
                now,
                now,
                now,
                now,
                now,
                now,
                now
        );
    }

    private static AppProperties properties(boolean workerEnabled) {
        return new AppProperties(
                "KORI",
                24,
                10,
                1000,
                new AppProperties.ProofIssuer("issuer", "public", "private"),
                new AppProperties.CoinManage("http://coin-manage", "api-key", 5000),
                new AppProperties.FoxCoin("http://fox-coin", "api-key", 5000),
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
                new AppProperties.Worker(
                        workerEnabled,
                        "worker",
                        60000,
                        3,
                        86_400_000L,
                        20,
                        20,
                        300_000L,
                        604_800_000L,
                        25
                )
        );
    }
}
