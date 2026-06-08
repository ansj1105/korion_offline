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
            int receiverHistoryPendingScanLimit
    ) {
        public Worker(boolean enabled, String consumerName, int claimIdleMs, int maxAttempts) {
            this(enabled, consumerName, claimIdleMs, maxAttempts, 86_400_000L, 20);
        }

        public Worker {
            receiverHistoryPendingTimeoutMs = receiverHistoryPendingTimeoutMs <= 0 ? 86_400_000L : receiverHistoryPendingTimeoutMs;
            receiverHistoryPendingScanLimit = receiverHistoryPendingScanLimit <= 0 ? 20 : receiverHistoryPendingScanLimit;
        }
    }
}
