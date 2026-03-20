package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.CoinManageSettlementPort;
import io.korion.offlinepay.contracts.internal.CoinManageFinalizeSettlementContract;
import io.korion.offlinepay.contracts.internal.InternalAckResponseContract;
import org.springframework.web.client.RestClient;

public class CoinManageSettlementAdapter implements CoinManageSettlementPort {

    private final RestClient restClient;

    public CoinManageSettlementAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void finalizeSettlement(SettlementLedgerCommand command) {
        InternalAckResponseContract response = restClient.post()
                .uri("/api/internal/offline-pay/settlements/finalize")
                .body(new CoinManageFinalizeSettlementContract(
                        command.settlementId(),
                        command.batchId(),
                        command.collateralId(),
                        command.proofId(),
                        command.userId(),
                        command.deviceId(),
                        command.assetCode(),
                        command.amount(),
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
}
