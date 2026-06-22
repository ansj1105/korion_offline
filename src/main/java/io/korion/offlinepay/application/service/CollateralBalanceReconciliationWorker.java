package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.application.port.OfflinePayReconcileCommandRepository;
import io.korion.offlinepay.application.port.ReconciliationCaseRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CollateralBalanceReconciliationWorker {

    private static final String CASE_TYPE = "COLLATERAL_PENDING_MISMATCH";
    private static final String REASON_CODE = "OFFLINE_COLLATERAL_PENDING_MISMATCH";
    private static final long RECONCILE_COMMAND_TTL_HOURS = 24;

    private final CollateralRepository collateralRepository;
    private final CoinManageSettlementPort coinManageSettlementPort;
    private final ReconciliationCaseRepository reconciliationCaseRepository;
    private final OfflinePayReconcileCommandRepository reconcileCommandRepository;
    private final TelegramAlertService telegramAlertService;
    private final JsonService jsonService;
    private final AppProperties properties;

    public CollateralBalanceReconciliationWorker(
            CollateralRepository collateralRepository,
            CoinManageSettlementPort coinManageSettlementPort,
            ReconciliationCaseRepository reconciliationCaseRepository,
            OfflinePayReconcileCommandRepository reconcileCommandRepository,
            TelegramAlertService telegramAlertService,
            JsonService jsonService,
            AppProperties properties
    ) {
        this.collateralRepository = collateralRepository;
        this.coinManageSettlementPort = coinManageSettlementPort;
        this.reconciliationCaseRepository = reconciliationCaseRepository;
        this.reconcileCommandRepository = reconcileCommandRepository;
        this.telegramAlertService = telegramAlertService;
        this.jsonService = jsonService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${offline-pay.worker.collateral-reconciliation-delay-ms:60000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }

        String assetCode = properties.assetCode();
        int size = properties.settlementStreamBatchSize();
        for (CollateralRepository.CollateralBalanceSummary summary : collateralRepository.summarizeActiveBalances(assetCode, size)) {
            reconcile(summary);
        }
    }

    private void reconcile(CollateralRepository.CollateralBalanceSummary summary) {
        CoinManageSettlementPort.PendingBalanceResult pending =
                coinManageSettlementPort.getOfflinePayPendingBalance(summary.userId(), summary.assetCode());
        BigDecimal offlineRemaining = summary.remainingAmount();
        BigDecimal coinManagePending = pending.offlinePayPendingBalance();
        String syntheticSettlementId = syntheticSettlementId(summary.userId(), summary.assetCode());

        if (offlineRemaining.compareTo(coinManagePending) == 0) {
            reconciliationCaseRepository.findOpenBySettlementIdAndCaseType(syntheticSettlementId, CASE_TYPE)
                    .ifPresent(existing -> reconciliationCaseRepository.resolve(
                            existing.id(),
                            mergeResolutionDetail(existing.detailJson(), offlineRemaining, coinManagePending)
                    ));
            return;
        }

        BigDecimal delta = coinManagePending.subtract(offlineRemaining);
        if (reconciliationCaseRepository.findOpenBySettlementIdAndCaseType(syntheticSettlementId, CASE_TYPE).isPresent()) {
            ensureReconcileCommand(summary, offlineRemaining, coinManagePending, delta);
            return;
        }

        ensureReconcileCommand(summary, offlineRemaining, coinManagePending, delta);
        reconciliationCaseRepository.save(
                syntheticSettlementId,
                "",
                "",
                "",
                CASE_TYPE,
                ReconciliationCaseStatus.OPEN,
                REASON_CODE,
                jsonService.write(Map.ofEntries(
                        Map.entry("userId", summary.userId()),
                        Map.entry("assetCode", summary.assetCode()),
                        Map.entry("offlineRemainingAmount", offlineRemaining.toPlainString()),
                        Map.entry("coinManageOfflinePayPendingBalance", coinManagePending.toPlainString()),
                        Map.entry("deltaAmount", delta.toPlainString()),
                        Map.entry("lockedAmount", summary.lockedAmount().toPlainString()),
                        Map.entry("syncTarget", "COIN_MANAGE_LEDGER"),
                        Map.entry("retryable", false),
                        Map.entry("adminAction", "REQUEUE_FAILED_SETTLEMENT_OR_INVESTIGATE"),
                        Map.entry("detectedAt", OffsetDateTime.now().toString())
                ))
        );
        telegramAlertService.notifyOperationalIssue(
                "offline_pay.collateral_reconciliation",
                "userId=" + summary.userId()
                        + ", assetCode=" + summary.assetCode()
                        + ", offlineRemaining=" + offlineRemaining.toPlainString()
                        + ", coinManagePending=" + coinManagePending.toPlainString()
                        + ", delta=" + delta.toPlainString()
        );
    }

    private void ensureReconcileCommand(
            CollateralRepository.CollateralBalanceSummary summary,
            BigDecimal offlineRemaining,
            BigDecimal coinManagePending,
            BigDecimal delta
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        if (reconcileCommandRepository
                .findRunnableByUserIdAndAssetCode(summary.userId(), summary.assetCode(), now)
                .isPresent()) {
            return;
        }
        reconcileCommandRepository.create(
                summary.userId(),
                summary.assetCode(),
                REASON_CODE,
                "collateral-balance:" + summary.assetCode() + ":" + now,
                UUID.randomUUID().toString(),
                now.plusHours(RECONCILE_COMMAND_TTL_HOURS),
                Map.ofEntries(
                        Map.entry("source", "COLLATERAL_BALANCE_RECONCILIATION_WORKER"),
                        Map.entry("userId", summary.userId()),
                        Map.entry("assetCode", summary.assetCode()),
                        Map.entry("offlineRemainingAmount", offlineRemaining.toPlainString()),
                        Map.entry("coinManageOfflinePayPendingBalance", coinManagePending.toPlainString()),
                        Map.entry("deltaAmount", delta.toPlainString()),
                        Map.entry("lockedAmount", summary.lockedAmount().toPlainString()),
                        Map.entry("detectedAt", now.toString())
                )
        );
    }

    private String syntheticSettlementId(long userId, String assetCode) {
        return "collateral-balance:" + userId + ":" + assetCode;
    }

    private String mergeResolutionDetail(String detailJson, BigDecimal offlineRemaining, BigDecimal coinManagePending) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
        jsonService.readTree(detailJson).fields().forEachRemaining(entry -> merged.put(entry.getKey(), entry.getValue()));
        merged.put("resolvedAt", OffsetDateTime.now().toString());
        merged.put("resolvedOfflineRemainingAmount", offlineRemaining.toPlainString());
        merged.put("resolvedCoinManageOfflinePayPendingBalance", coinManagePending.toPlainString());
        return jsonService.write(merged);
    }
}
