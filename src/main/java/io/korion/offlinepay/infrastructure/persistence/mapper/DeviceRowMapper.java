package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.Device;
import io.korion.offlinepay.domain.status.DeviceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class DeviceRowMapper implements RowMapper<Device> {

    @Override
    public Device mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new Device(
                resultSet.getString("id"),
                resultSet.getString("device_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("public_key"),
                resultSet.getInt("key_version"),
                DeviceStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("metadata"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}

