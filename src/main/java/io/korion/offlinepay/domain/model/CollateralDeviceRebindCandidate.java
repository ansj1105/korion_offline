package io.korion.offlinepay.domain.model;

public record CollateralDeviceRebindCandidate(
        CollateralLock collateral,
        String targetDeviceId
) {}
