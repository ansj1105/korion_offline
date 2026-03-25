package io.korion.offlinepay.config;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.application.service.SimpleCircuitBreaker;
import io.korion.offlinepay.application.service.TelegramAlertService;
import io.korion.offlinepay.infrastructure.adapter.CoinManageCollateralAdapter;
import io.korion.offlinepay.infrastructure.adapter.CoinManageSettlementAdapter;
import io.korion.offlinepay.infrastructure.adapter.CircuitBreakingCoinManageSettlementAdapter;
import io.korion.offlinepay.infrastructure.adapter.CircuitBreakingFoxCoinHistoryAdapter;
import io.korion.offlinepay.infrastructure.adapter.FoxCoinHistoryAdapter;
import io.korion.offlinepay.infrastructure.adapter.FoxCoinWalletSnapshotAdapter;
import io.korion.offlinepay.infrastructure.events.JdbcSettlementBatchEventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

@Configuration
public class InfrastructureFactoryConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient telegramRestClient(RestClient.Builder builder) {
        return builder.baseUrl("https://api.telegram.org").build();
    }

    @Bean
    public TelegramAlertService telegramAlertService(RestClient telegramRestClient, AppProperties properties) {
        return new TelegramAlertService(telegramRestClient, properties);
    }

    @Bean
    public SimpleCircuitBreaker coinManageSettlementCircuitBreaker(AppProperties properties) {
        return new SimpleCircuitBreaker(resolveFailureThreshold(properties), resolveResetTimeoutMs(properties));
    }

    @Bean
    public SimpleCircuitBreaker foxCoinHistoryCircuitBreaker(AppProperties properties) {
        return new SimpleCircuitBreaker(resolveFailureThreshold(properties), resolveResetTimeoutMs(properties));
    }

    @Bean
    public RestClient coinManageRestClient(RestClient.Builder builder, AppProperties properties) {
        return builder
                .baseUrl(properties.coinManage().baseUrl())
                .build();
    }

    @Bean
    public RestClient foxCoinRestClient(RestClient.Builder builder, AppProperties properties) {
        return builder
                .baseUrl(properties.foxCoin().baseUrl())
                .build();
    }

    @Bean
    public CoinManageCollateralPort coinManageCollateralPort(RestClient coinManageRestClient, AppProperties properties) {
        return new CoinManageCollateralAdapter(coinManageRestClient, properties.coinManage().apiKey());
    }

    @Bean
    public CoinManageSettlementPort coinManageSettlementPort(
            RestClient coinManageRestClient,
            AppProperties properties,
            SimpleCircuitBreaker coinManageSettlementCircuitBreaker,
            TelegramAlertService telegramAlertService
    ) {
        return new CircuitBreakingCoinManageSettlementAdapter(
                new CoinManageSettlementAdapter(coinManageRestClient, properties.coinManage().apiKey()),
                coinManageSettlementCircuitBreaker,
                telegramAlertService
        );
    }

    @Bean
    public FoxCoinHistoryPort foxCoinHistoryPort(
            RestClient foxCoinRestClient,
            AppProperties properties,
            SimpleCircuitBreaker foxCoinHistoryCircuitBreaker,
            TelegramAlertService telegramAlertService
    ) {
        return new CircuitBreakingFoxCoinHistoryAdapter(
                new FoxCoinHistoryAdapter(foxCoinRestClient, properties.foxCoin().apiKey()),
                foxCoinHistoryCircuitBreaker,
                telegramAlertService
        );
    }

    @Bean
    public FoxCoinWalletSnapshotPort foxCoinWalletSnapshotPort(
            RestClient foxCoinRestClient,
            AppProperties properties
    ) {
        return new FoxCoinWalletSnapshotAdapter(foxCoinRestClient, properties.foxCoin().apiKey());
    }

    @Bean
    public SettlementBatchEventBus settlementBatchEventBus(
            JdbcClient jdbcClient,
            AppProperties properties,
            TelegramAlertService telegramAlertService
    ) {
        return new JdbcSettlementBatchEventBus(jdbcClient, properties, telegramAlertService);
    }

    private int resolveFailureThreshold(AppProperties properties) {
        if (properties.alerts() == null || properties.alerts().circuitBreaker() == null) {
            return 3;
        }
        return properties.alerts().circuitBreaker().failureThreshold();
    }

    private long resolveResetTimeoutMs(AppProperties properties) {
        if (properties.alerts() == null || properties.alerts().circuitBreaker() == null) {
            return 60_000L;
        }
        return properties.alerts().circuitBreaker().resetTimeoutMs();
    }
}
