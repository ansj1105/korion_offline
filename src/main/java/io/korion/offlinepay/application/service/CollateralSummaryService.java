package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.CollateralRepository;
import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.domain.model.CollateralLock;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollateralSummaryService {

    private static final String CANONICAL_BASIS = "OFFLINE_PAY_APPROVED_COLLATERAL";

    private final CollateralRepository collateralRepository;
    private final AppProperties properties;

    public CollateralSummaryService(
            CollateralRepository collateralRepository,
            AppProperties properties
    ) {
        this.collateralRepository = collateralRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public CollateralAggregateSummary getAggregateSummary(long userId, String assetCode) {
        String normalizedAssetCode = assetCode == null || assetCode.isBlank()
                ? properties.assetCode()
                : assetCode.trim().toUpperCase();
        CollateralLock aggregate = collateralRepository
                .findAggregateByUserIdAndAssetCode(userId, normalizedAssetCode)
                .orElse(null);
        BigDecimal lockedAmount = aggregate == null ? BigDecimal.ZERO : aggregate.lockedAmount().max(BigDecimal.ZERO);
        BigDecimal remainingAmount = aggregate == null ? BigDecimal.ZERO : aggregate.remainingAmount().max(BigDecimal.ZERO);
        return new CollateralAggregateSummary(
                userId,
                normalizedAssetCode,
                lockedAmount.toPlainString(),
                remainingAmount.toPlainString(),
                CANONICAL_BASIS,
                OffsetDateTime.now().toString()
        );
    }

    public record CollateralAggregateSummary(
            long userId,
            String assetCode,
            String lockedAmount,
            String remainingAmount,
            String canonicalBasis,
            String refreshedAt
    ) {}
}
