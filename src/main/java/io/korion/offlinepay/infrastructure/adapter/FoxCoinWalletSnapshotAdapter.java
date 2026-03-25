package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.client.RestClient;

public class FoxCoinWalletSnapshotAdapter implements FoxCoinWalletSnapshotPort {

    private final RestClient restClient;
    private final String apiKey;

    public FoxCoinWalletSnapshotAdapter(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public WalletSnapshot getCanonicalWalletSnapshot(long userId, String assetCode) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/internal/wallets/snapshot")
                        .queryParam("userId", userId)
                        .queryParam("currencyCode", assetCode)
                        .build())
                .header("x-internal-api-key", apiKey)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new IllegalStateException("fox_coin wallet snapshot response is empty");
        }
        return new WalletSnapshot(
                userId,
                stringValue(response.get("currencyCode"), assetCode),
                decimalValue(response.get("totalBalance")),
                decimalValue(response.get("lockedBalance")),
                stringValue(response.get("canonicalBasis"), "FOX_INTERNAL_KORI_BALANCE"),
                stringValue(response.get("refreshedAt"), "")
        );
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
