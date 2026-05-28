package io.korion.offlinepay.contracts.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoinManageFinalizeSettlementContractTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void omitsNullReceiverFieldsForCoinManageOptionalContract() throws Exception {
        CoinManageFinalizeSettlementContract contract = new CoinManageFinalizeSettlementContract(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                "1",
                "sender-device-1",
                null,
                null,
                "KORI",
                new BigDecimal("1.000000").toPlainString(),
                new BigDecimal("0.001000").toPlainString(),
                "SETTLED",
                "RELEASE",
                false,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "new-state",
                "previous-state",
                1L,
                "nonce-1",
                "signature-1"
        );

        String json = objectMapper.writeValueAsString(contract);

        assertFalse(json.contains("receiverUserId"));
        assertFalse(json.contains("receiverDeviceId"));
    }

    @Test
    void includesReceiverFieldsWhenReceiverDeviceIsKnown() throws Exception {
        CoinManageFinalizeSettlementContract contract = new CoinManageFinalizeSettlementContract(
                "settlement-1",
                "batch-1",
                "collateral-1",
                "proof-1",
                "1",
                "sender-device-1",
                "39",
                "receiver-device-1",
                "KORI",
                new BigDecimal("1.000000").toPlainString(),
                new BigDecimal("0.001000").toPlainString(),
                "SETTLED",
                "RELEASE",
                false,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "new-state",
                "previous-state",
                1L,
                "nonce-1",
                "signature-1"
        );

        String json = objectMapper.writeValueAsString(contract);

        assertTrue(json.contains("\"receiverUserId\":\"39\""));
        assertTrue(json.contains("\"receiverDeviceId\":\"receiver-device-1\""));
    }
}
