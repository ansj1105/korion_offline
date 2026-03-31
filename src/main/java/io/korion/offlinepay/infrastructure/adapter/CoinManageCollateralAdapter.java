package io.korion.offlinepay.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.contracts.internal.CoinManageLockCollateralContract;
import io.korion.offlinepay.contracts.internal.CoinManageLockCollateralResponseContract;
import io.korion.offlinepay.contracts.internal.CoinManageReleaseCollateralContract;
import io.korion.offlinepay.contracts.internal.CoinManageReleaseCollateralResponseContract;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

public class CoinManageCollateralAdapter implements CoinManageCollateralPort {

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public CoinManageCollateralAdapter(RestClient restClient, String apiKey, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public LockCollateralResult lockCollateral(long userId, String deviceId, String assetCode, BigDecimal amount, String referenceId, int policyVersion) {
        CoinManageLockCollateralResponseContract response = parseResponse(
                restClient.post()
                .uri("/api/internal/offline-pay/collateral/lock")
                .header("x-internal-api-key", apiKey)
                .body(new CoinManageLockCollateralContract(
                        String.valueOf(userId),
                        deviceId,
                        assetCode,
                        formatAmount(amount),
                        referenceId,
                        policyVersion
                ))
                .exchange((request, clientResponse) -> readResponseBody(clientResponse.getStatusCode(), clientResponse.getBody())),
                CoinManageLockCollateralResponseContract.class,
                "lock"
        );
        if (response == null) {
            throw new IllegalStateException("coin_manage lock response is empty");
        }
        return new LockCollateralResult(response.lockId(), response.status());
    }

    @Override
    public ReleaseCollateralResult releaseCollateral(long userId, String deviceId, String collateralId, String assetCode, BigDecimal amount, String referenceId) {
        CoinManageReleaseCollateralResponseContract response = parseResponse(
                restClient.post()
                .uri("/api/internal/offline-pay/collateral/release")
                .header("x-internal-api-key", apiKey)
                .body(new CoinManageReleaseCollateralContract(
                        String.valueOf(userId),
                        deviceId,
                        collateralId,
                        assetCode,
                        formatAmount(amount),
                        referenceId
                ))
                .exchange((request, clientResponse) -> readResponseBody(clientResponse.getStatusCode(), clientResponse.getBody())),
                CoinManageReleaseCollateralResponseContract.class,
                "release"
        );
        if (response == null) {
            throw new IllegalStateException("coin_manage release response is empty");
        }
        return new ReleaseCollateralResult(response.releaseId(), response.status());
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private <T> T parseResponse(byte[] body, Class<T> responseType, String operationName) {
        if (body == null || body.length == 0) {
            throw new IllegalStateException("coin_manage collateral " + operationName + " response is empty");
        }
        try {
            return objectMapper.readValue(body, responseType);
        } catch (Exception exception) {
            String rawBody = new String(body, StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    "coin_manage collateral " + operationName + " response parse failed: " + rawBody,
                    exception
            );
        }
    }

    private byte[] readResponseBody(HttpStatusCode statusCode, java.io.InputStream bodyStream) {
        try {
            byte[] body = StreamUtils.copyToByteArray(bodyStream);
            if (statusCode.isError()) {
                String rawBody = new String(body, StandardCharsets.UTF_8);
                throw new IllegalStateException("coin_manage collateral request failed with status "
                        + statusCode.value() + ": " + rawBody);
            }
            return body;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read coin_manage collateral response body", exception);
        }
    }
}
