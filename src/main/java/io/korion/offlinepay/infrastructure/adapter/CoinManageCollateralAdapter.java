package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.contracts.internal.CoinManageLockCollateralContract;
import io.korion.offlinepay.contracts.internal.CoinManageLockCollateralResponseContract;
import java.math.BigDecimal;
import org.springframework.web.client.RestClient;

public class CoinManageCollateralAdapter implements CoinManageCollateralPort {

    private final RestClient restClient;

    public CoinManageCollateralAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public LockCollateralResult lockCollateral(long userId, String deviceId, String assetCode, BigDecimal amount, String referenceId, int policyVersion) {
        CoinManageLockCollateralResponseContract response = restClient.post()
                .uri("/internal/offline-pay/collateral/lock")
                .body(new CoinManageLockCollateralContract(
                        userId,
                        deviceId,
                        assetCode,
                        amount,
                        referenceId,
                        policyVersion
                ))
                .retrieve()
                .body(CoinManageLockCollateralResponseContract.class);
        if (response == null) {
            throw new IllegalStateException("coin_manage lock response is empty");
        }
        return new LockCollateralResult(response.lockId(), response.status());
    }
}
