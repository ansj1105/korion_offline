package io.korion.offlinepay.config;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.application.port.SettlementBatchEventBus;
import io.korion.offlinepay.infrastructure.adapter.CoinManageCollateralAdapter;
import io.korion.offlinepay.infrastructure.adapter.CoinManageSettlementAdapter;
import io.korion.offlinepay.infrastructure.adapter.FoxCoinHistoryAdapter;
import io.korion.offlinepay.infrastructure.events.RedisSettlementBatchEventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class InfrastructureFactoryConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient coinManageRestClient(RestClient.Builder builder, AppProperties properties) {
        return builder
                .baseUrl(properties.coinManage().baseUrl())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("x-internal-api-key", properties.coinManage().apiKey());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public RestClient foxCoinRestClient(RestClient.Builder builder, AppProperties properties) {
        return builder
                .baseUrl(properties.foxCoin().baseUrl())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("x-internal-api-key", properties.foxCoin().apiKey());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public CoinManageCollateralPort coinManageCollateralPort(RestClient coinManageRestClient) {
        return new CoinManageCollateralAdapter(coinManageRestClient);
    }

    @Bean
    public CoinManageSettlementPort coinManageSettlementPort(RestClient coinManageRestClient) {
        return new CoinManageSettlementAdapter(coinManageRestClient);
    }

    @Bean
    public FoxCoinHistoryPort foxCoinHistoryPort(RestClient foxCoinRestClient) {
        return new FoxCoinHistoryAdapter(foxCoinRestClient);
    }

    @Bean
    public SettlementBatchEventBus settlementBatchEventBus(StringRedisTemplate redisTemplate, AppProperties properties) {
        return new RedisSettlementBatchEventBus(redisTemplate, properties);
    }
}
