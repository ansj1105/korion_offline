package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.FoxCoinStoreProductPolicyPort;
import java.util.Map;
import org.springframework.web.client.RestClient;

public class FoxCoinStoreProductPolicyAdapter implements FoxCoinStoreProductPolicyPort {

    private final RestClient restClient;
    private final String apiKey;

    public FoxCoinStoreProductPolicyAdapter(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public StoreProductPolicy getStoreProductPolicy(long userId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/internal/offline-pay/store-product-policy")
                        .queryParam("userId", userId)
                        .build())
                .header("x-internal-api-key", apiKey)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new IllegalStateException("fox_coin store product policy response is empty");
        }
        return new StoreProductPolicy(
                longValue(response.get("userId"), userId),
                intValue(response.get("level"), 1),
                intValue(response.get("storeProductLimit"), 0),
                intValue(response.get("dailyMaxAds"), 5),
                booleanValue(response.get("profilePhotoEnabled"))
        );
    }

    private static long longValue(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
