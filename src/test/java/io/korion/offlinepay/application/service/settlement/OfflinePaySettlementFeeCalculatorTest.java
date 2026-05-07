package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OfflinePaySettlementFeeCalculatorTest {

    private final OfflinePaySettlementFeeCalculator calculator = new OfflinePaySettlementFeeCalculator();

    @Test
    void calculatesConfiguredKoriSettlementFee() {
        assertEquals(new BigDecimal("0.004000"), calculator.calculateFee("kori", new BigDecimal("1.000000")));
        assertEquals(new BigDecimal("1.004000"), calculator.calculateTotal("KORI", new BigDecimal("1.000000")));
    }

    @Test
    void leavesAssetsWithoutFeePolicyUntouched() {
        assertEquals(new BigDecimal("0.000000"), calculator.calculateFee("USDT", new BigDecimal("1.000000")));
        assertEquals(new BigDecimal("1.000000"), calculator.calculateTotal("USDT", new BigDecimal("1.000000")));
    }
}
