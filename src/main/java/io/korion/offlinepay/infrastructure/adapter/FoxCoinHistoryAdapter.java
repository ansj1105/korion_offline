package io.korion.offlinepay.infrastructure.adapter;

import io.korion.offlinepay.application.port.FoxCoinHistoryPort;
import io.korion.offlinepay.contracts.internal.FoxCoinRecordSettlementHistoryContract;
import io.korion.offlinepay.contracts.internal.InternalAckResponseContract;
import org.springframework.web.client.RestClient;

public class FoxCoinHistoryAdapter implements FoxCoinHistoryPort {

    private final RestClient restClient;

    public FoxCoinHistoryAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void recordSettlementHistory(SettlementHistoryCommand command) {
        InternalAckResponseContract response = restClient.post()
                .uri("/internal/offline-pay/settlements/history")
                .body(new FoxCoinRecordSettlementHistoryContract(
                        command.settlementId(),
                        command.batchId(),
                        command.collateralId(),
                        command.proofId(),
                        command.userId(),
                        command.deviceId(),
                        command.assetCode(),
                        command.amount(),
                        command.settlementStatus(),
                        command.historyType()
                ))
                .retrieve()
                .body(InternalAckResponseContract.class);
        if (response == null) {
            throw new IllegalStateException("fox_coin settlement history response is empty");
        }
    }
}
