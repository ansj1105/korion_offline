package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository {

    Optional<Device> findByDeviceId(String deviceId);

    Optional<Device> findByUserIdAndDeviceId(long userId, String deviceId);

    List<Device> findActiveByUserId(long userId);

    Device refreshRegistration(long userId, String deviceId, String publicKey, int keyVersion, String metadataJson);

    List<Device> findRecent(int size, DeviceStatus status);

    Device save(long userId, String deviceId, String publicKey, int keyVersion, String metadataJson);

    Device updateProfile(long userId, String deviceId, String metadataJson);

    void revoke(String deviceId, Integer keyVersion, String metadataJson);
}
