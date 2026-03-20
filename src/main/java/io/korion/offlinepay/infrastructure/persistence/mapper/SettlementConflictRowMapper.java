package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.SettlementConflict;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class SettlementConflictRowMapper implements RowMapper<SettlementConflict> {

    @Override
    public SettlementConflict mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SettlementConflict(
                resultSet.getString("id"),
                resultSet.getString("settlement_id"),
                resultSet.getString("voucher_id"),
                resultSet.getString("collateral_id"),
                resultSet.getString("device_id"),
                resultSet.getString("conflict_type"),
                resultSet.getString("severity"),
                resultSet.getString("status"),
                resultSet.getString("detail"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
