package io.korion.offlinepay.application.service.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OfflinePaySettlementFeeCalculator {

    private static final Map<String, BigDecimal> OFFLINE_PAY_SETTLEMENT_FEE_RATES = Map.of(
            "KORI", new BigDecimal("0.004")
    );
    private static final int AMOUNT_SCALE = 6;

    public BigDecimal calculateFee(String assetCode, BigDecimal amount) {
        BigDecimal feeRate = feeRateFor(assetCode);
        if (feeRate.signum() <= 0 || amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return normalize(amount).multiply(feeRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateFee(BigDecimal amount) {
        return calculateFee("KORI", amount);
    }

    public BigDecimal calculateTotal(String assetCode, BigDecimal amount) {
        return normalize(amount);
    }

    public BigDecimal calculateTotal(BigDecimal amount) {
        return calculateTotal("KORI", amount);
    }

    public BigDecimal calculateReceiverAmount(String assetCode, BigDecimal amount) {
        BigDecimal normalizedAmount = normalize(amount);
        BigDecimal feeAmount = calculateFee(assetCode, normalizedAmount);
        BigDecimal receiverAmount = normalizedAmount.subtract(feeAmount);
        if (receiverAmount.signum() < 0) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return receiverAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    public boolean hasFeePolicy(String assetCode) {
        return OFFLINE_PAY_SETTLEMENT_FEE_RATES.containsKey(normalizeAssetCode(assetCode));
    }

    private BigDecimal feeRateFor(String assetCode) {
        return OFFLINE_PAY_SETTLEMENT_FEE_RATES.getOrDefault(normalizeAssetCode(assetCode), BigDecimal.ZERO);
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount == null
                ? BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)
                : amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeAssetCode(String assetCode) {
        return assetCode == null ? "" : assetCode.trim().toUpperCase(Locale.ROOT);
    }
}
