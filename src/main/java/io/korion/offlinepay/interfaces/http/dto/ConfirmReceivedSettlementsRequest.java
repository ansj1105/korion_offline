package io.korion.offlinepay.interfaces.http.dto;

import io.korion.offlinepay.application.service.SettlementApplicationService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ConfirmReceivedSettlementsRequest(
        @Positive long userId,
        @NotEmpty List<String> proofIds
) {
    public SettlementApplicationService.ConfirmReceivedSettlementsCommand toCommand() {
        return new SettlementApplicationService.ConfirmReceivedSettlementsCommand(userId, proofIds);
    }
}
