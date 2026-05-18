package io.korion.offlinepay.contracts.internal;

public record CoinManageDeviceUpsertContract(
        String userId,
        String deviceId,
        String status,
        Integer keyVersion
) {}
