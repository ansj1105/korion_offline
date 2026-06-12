package io.korion.offlinepay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "offline-pay")
public record AppProperties(
        String assetCode,
        int defaultCollateralExpiryHours,
        int settlementStreamBatchSize,
        int settlementStreamBlockMs,
        ProofIssuer proofIssuer,
        CoinManage coinManage,
        FoxCoin foxCoin,
        Alerts alerts,
        Redis redis,
        Worker worker
) {
    public AppProperties {
        settlementStreamBatchSize = settlementStreamBatchSize <= 0 ? 20 : settlementStreamBatchSize;
        settlementStreamBlockMs = settlementStreamBlockMs <= 0 ? 1000 : settlementStreamBlockMs;
        if (worker == null) {
            worker = new Worker(
                    envBoolean("SETTLEMENT_WORKER_ENABLED", false),
                    envString("SETTLEMENT_CONSUMER_NAME", "offline-pay-worker"),
                    envInt("SETTLEMENT_WORKER_CLAIM_IDLE_MS", 60_000),
                    envInt("SETTLEMENT_WORKER_MAX_ATTEMPTS", 3),
                    envLong("RECEIVER_HISTORY_PENDING_TIMEOUT_MS", 86_400_000L),
                    envInt("RECEIVER_HISTORY_PENDING_SCAN_LIMIT", 20),
                    envInt("LOCAL_EVIDENCE_RECONCILIATION_LIMIT", 20)
            );
        }
    }

    public record ProofIssuer(
            String keyId,
            String publicKey,
            String privateKey
    ) {}

    public record CoinManage(
            String baseUrl,
            String apiKey,
            int timeoutMs
    ) {}

    public record FoxCoin(
            String baseUrl,
            String apiKey,
            int timeoutMs
    ) {}

    public record Alerts(
            Telegram telegram,
            CircuitBreaker circuitBreaker
    ) {}

    public record Telegram(
            String botToken,
            String chatId
    ) {}

    public record CircuitBreaker(
            int failureThreshold,
            long resetTimeoutMs
    ) {}

    public record Redis(
            String keyPrefix,
            String settlementRequestedStream,
            String settlementResultStream,
            String settlementConflictStream,
            String settlementDeadLetterStream,
            String collateralRequestedStream,
            String collateralResultStream,
            String settlementGroup
    ) {}

    public record Worker(
            boolean enabled,
            String consumerName,
            int claimIdleMs,
            int maxAttempts,
            long receiverHistoryPendingTimeoutMs,
            int receiverHistoryPendingScanLimit,
            int localEvidenceReconciliationLimit
    ) {
        public Worker(boolean enabled, String consumerName, int claimIdleMs, int maxAttempts) {
            this(enabled, consumerName, claimIdleMs, maxAttempts, 86_400_000L, 20, 20);
        }

        public Worker {
            receiverHistoryPendingTimeoutMs = receiverHistoryPendingTimeoutMs <= 0 ? 86_400_000L : receiverHistoryPendingTimeoutMs;
            receiverHistoryPendingScanLimit = receiverHistoryPendingScanLimit <= 0 ? 20 : receiverHistoryPendingScanLimit;
            localEvidenceReconciliationLimit = localEvidenceReconciliationLimit <= 0 ? 20 : localEvidenceReconciliationLimit;
        }
    }

    private static boolean envBoolean(String name, boolean fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static String envString(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int envInt(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long envLong(String name, long fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
