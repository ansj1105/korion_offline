package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.DeviceRepository;
import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.infrastructure.persistence.mapper.DeviceRowMapper;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeviceRepository implements DeviceRepository {

    private final JdbcClient jdbcClient;
    private final DeviceRowMapper deviceRowMapper;

    public JdbcDeviceRepository(JdbcClient jdbcClient, DeviceRowMapper deviceRowMapper) {
        this.jdbcClient = jdbcClient;
        this.deviceRowMapper = deviceRowMapper;
    }

    @Override
    public Optional<Device> findByDeviceId(String deviceId) {
        String sql = QueryBuilder
                .select("devices", "id", "device_id", "user_id", "public_key", "key_version", "status", "metadata", "created_at", "updated_at")
                .where("device_id = :deviceId")
                .build();
        return jdbcClient.sql(sql)
                .param("deviceId", deviceId)
                .query(deviceRowMapper)
                .optional();
    }

    @Override
    public Device save(long userId, String deviceId, String publicKey, int keyVersion, String metadataJson) {
        String sql = QueryBuilder
                .insert("devices", "device_id", "user_id", "public_key", "key_version", "status", "metadata")
                .build();
        jdbcClient.sql(sql.replace(":status", "'ACTIVE'").replace(":metadata", "CAST(:metadata AS jsonb)"))
                .param("deviceId", deviceId)
                .param("userId", userId)
                .param("publicKey", publicKey)
                .param("keyVersion", keyVersion)
                .param("metadata", metadataJson)
                .update();

        return findByDeviceId(deviceId).orElseThrow();
    }

    @Override
    public void revoke(String deviceId, Integer keyVersion, String metadataJson) {
        QueryBuilder.UpdateBuilder builder = QueryBuilder
                .update("devices")
                .set("status = 'REVOKED'")
                .set("metadata = metadata || CAST(:metadata AS jsonb)")
                .touchUpdatedAt()
                .where("device_id = :deviceId");
        String sql = keyVersion == null
                ? builder.where("status = 'ACTIVE'").build()
                : builder.where("key_version = :keyVersion").build();
        JdbcClient.StatementSpec statementSpec = jdbcClient.sql(sql)
                .param("deviceId", deviceId)
                .param("metadata", metadataJson);
        if (keyVersion != null) {
            statementSpec.param("keyVersion", keyVersion);
        }
        statementSpec.update();
    }
}
