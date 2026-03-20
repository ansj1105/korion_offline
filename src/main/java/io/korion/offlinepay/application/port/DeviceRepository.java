package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.Device;
import java.util.Optional;

public interface DeviceRepository {

    Optional<Device> findByDeviceId(String deviceId);

    Device save(long userId, String deviceId, String publicKey, int keyVersion, String metadataJson);

    void revoke(String deviceId, Integer keyVersion, String metadataJson);
}
