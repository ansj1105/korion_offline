package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.CollateralLock;
import io.korion.offlinepay.domain.status.CollateralStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class CollateralLockRowMapper implements RowMapper<CollateralLock> {

    @Override
    public CollateralLock mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new CollateralLock(
                resultSet.getString("id"),
                resultSet.getLong("user_id"),
                resultSet.getString("device_id"),
                resultSet.getString("asset_code"),
                resultSet.getBigDecimal("locked_amount"),
                resultSet.getBigDecimal("remaining_amount"),
                resultSet.getString("initial_state_root"),
                resultSet.getInt("policy_version"),
                CollateralStatus.fromPersistence(resultSet.getString("status")),
                resultSet.getString("external_lock_id"),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getString("metadata"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
