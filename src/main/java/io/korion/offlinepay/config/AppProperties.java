package io.korion.offlinepay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "offline-pay")
public record AppProperties(
        String assetCode,
        int defaultCollateralExpiryHours,
        int settlementStreamBatchSize,
        int settlementStreamBlockMs,
        CoinManage coinManage,
        FoxCoin foxCoin,
        Redis redis,
        Worker worker
) {

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

    public record Redis(
            String keyPrefix,
            String settlementRequestedStream,
            String settlementResultStream,
            String settlementConflictStream,
            String settlementDeadLetterStream,
            String settlementGroup
    ) {}

    public record Worker(
            boolean enabled,
            String consumerName,
            int claimIdleMs,
            int maxAttempts
    ) {}
}
