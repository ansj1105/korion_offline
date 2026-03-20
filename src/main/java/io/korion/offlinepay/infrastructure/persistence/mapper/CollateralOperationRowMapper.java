package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.CollateralOperation;
import io.korion.offlinepay.domain.status.CollateralOperationStatus;
import io.korion.offlinepay.domain.status.CollateralOperationType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class CollateralOperationRowMapper implements RowMapper<CollateralOperation> {

    @Override
    public CollateralOperation mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new CollateralOperation(
                resultSet.getString("id"),
                resultSet.getString("collateral_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("device_id"),
                resultSet.getString("asset_code"),
                CollateralOperationType.valueOf(resultSet.getString("operation_type")),
                resultSet.getBigDecimal("amount"),
                CollateralOperationStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("reference_id"),
                resultSet.getString("error_message"),
                resultSet.getString("metadata"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
