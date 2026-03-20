package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.SettlementResultRecord;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class SettlementResultRowMapper implements RowMapper<SettlementResultRecord> {

    @Override
    public SettlementResultRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SettlementResultRecord(
                resultSet.getString("id"),
                resultSet.getString("settlement_id"),
                resultSet.getString("batch_id"),
                resultSet.getString("voucher_id"),
                resultSet.getString("collateral_id"),
                resultSet.getString("sender_device_id"),
                resultSet.getString("receiver_device_id"),
                SettlementStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("reason_code"),
                resultSet.getString("detail"),
                resultSet.getBigDecimal("settled_amount"),
                resultSet.getObject("processed_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }
}
