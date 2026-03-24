package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.contracts.internal.CoinManageCompensateSettlementContract;
import io.korion.offlinepay.contracts.internal.CoinManageFinalizeSettlementContract;
import io.korion.offlinepay.contracts.internal.InternalAckResponseContract;
import java.math.RoundingMode;
import org.springframework.web.client.RestClient;

public class CoinManageSettlementAdapter implements CoinManageSettlementPort {

    private final RestClient restClient;
    private final String apiKey;

    public CoinManageSettlementAdapter(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public void finalizeSettlement(SettlementLedgerCommand command) {
        InternalAckResponseContract response = restClient.post()
                .uri("/api/internal/offline-pay/settlements/finalize")
                .header("x-internal-api-key", apiKey)
                .body(new CoinManageFinalizeSettlementContract(
                        command.settlementId(),
                        command.batchId(),
                        command.collateralId(),
                        command.proofId(),
                        String.valueOf(command.userId()),
                        command.deviceId(),
                        command.assetCode(),
                        command.amount().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                        command.settlementStatus(),
                        command.releaseAction(),
                        command.conflictDetected(),
                        command.proofFingerprint(),
                        command.newStateHash(),
                        command.previousHash(),
                        command.monotonicCounter(),
                        command.nonce(),
                        command.signature()
                ))
                .retrieve()
                .body(InternalAckResponseContract.class);
        if (response == null) {
            throw new IllegalStateException("coin_manage settlement finalize response is empty");
        }
    }

    @Override
    public void compensateSettlement(SettlementCompensationCommand command) {
        InternalAckResponseContract response = restClient.post()
                .uri("/api/internal/offline-pay/settlements/compensate")
                .header("x-internal-api-key", apiKey)
                .body(new CoinManageCompensateSettlementContract(
                        command.settlementId(),
                        command.batchId(),
                        command.collateralId(),
                        command.proofId(),
                        String.valueOf(command.userId()),
                        command.deviceId(),
                        command.assetCode(),
                        command.amount().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                        command.releaseAction(),
                        command.proofFingerprint(),
                        command.compensationReason()
                ))
                .retrieve()
                .body(InternalAckResponseContract.class);
        if (response == null) {
            throw new IllegalStateException("coin_manage settlement compensate response is empty");
        }
    }
}
