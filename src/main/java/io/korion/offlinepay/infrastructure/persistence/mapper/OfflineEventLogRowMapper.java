package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.OfflineEventLog;
import io.korion.offlinepay.domain.status.OfflineEventStatus;
import io.korion.offlinepay.domain.status.OfflineEventType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class OfflineEventLogRowMapper implements RowMapper<OfflineEventLog> {

    @Override
    public OfflineEventLog mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new OfflineEventLog(
                resultSet.getString("id"),
                resultSet.getLong("user_id"),
                resultSet.getString("device_id"),
                OfflineEventType.valueOf(resultSet.getString("event_type")),
                OfflineEventStatus.valueOf(resultSet.getString("event_status")),
                resultSet.getString("asset_code"),
                resultSet.getString("network_code"),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("request_id"),
                resultSet.getString("settlement_id"),
                resultSet.getString("counterparty_device_id"),
                resultSet.getString("counterparty_actor"),
                resultSet.getString("reason_code"),
                resultSet.getString("message"),
                resultSet.getString("metadata"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
