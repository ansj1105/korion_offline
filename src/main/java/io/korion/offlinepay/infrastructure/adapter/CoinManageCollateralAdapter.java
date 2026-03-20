package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.CoinManageCollateralPort;
import io.korion.offlinepay.contracts.internal.CoinManageLockCollateralContract;
import io.korion.offlinepay.contracts.internal.CoinManageLockCollateralResponseContract;
import io.korion.offlinepay.contracts.internal.CoinManageReleaseCollateralContract;
import io.korion.offlinepay.contracts.internal.CoinManageReleaseCollateralResponseContract;
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
                .uri("/api/internal/offline-pay/collateral/lock")
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

    @Override
    public ReleaseCollateralResult releaseCollateral(long userId, String deviceId, String collateralId, String assetCode, BigDecimal amount, String referenceId) {
        CoinManageReleaseCollateralResponseContract response = restClient.post()
                .uri("/api/internal/offline-pay/collateral/release")
                .body(new CoinManageReleaseCollateralContract(
                        userId,
                        deviceId,
                        collateralId,
                        assetCode,
                        amount,
                        referenceId
                ))
                .retrieve()
                .body(CoinManageReleaseCollateralResponseContract.class);
        if (response == null) {
            throw new IllegalStateException("coin_manage release response is empty");
        }
        return new ReleaseCollateralResult(response.releaseId(), response.status());
    }
}
