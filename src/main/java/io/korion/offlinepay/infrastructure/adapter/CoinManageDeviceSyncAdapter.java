package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.CoinManageDeviceSyncPort;
import io.korion.offlinepay.contracts.internal.CoinManageDeviceUpsertContract;
import org.springframework.web.client.RestClient;

public class CoinManageDeviceSyncAdapter implements CoinManageDeviceSyncPort {

    private final RestClient restClient;
    private final String apiKey;

    public CoinManageDeviceSyncAdapter(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public void upsertDevice(DeviceSyncCommand command) {
        restClient.post()
                .uri("/api/internal/offline-pay/devices/upsert")
                .header("x-internal-api-key", apiKey)
                .body(new CoinManageDeviceUpsertContract(
                        String.valueOf(command.userId()),
                        command.deviceId(),
                        command.status(),
                        command.keyVersion()
                ))
                .retrieve()
                .toBodilessEntity();
    }
}
