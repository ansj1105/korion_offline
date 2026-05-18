package io.korion.offlinepay.application.port;

public interface CoinManageDeviceSyncPort {

    void upsertDevice(DeviceSyncCommand command);

    record DeviceSyncCommand(
            long userId,
            String deviceId,
            String status,
            Integer keyVersion
    ) {}
}
