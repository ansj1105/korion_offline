package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.SettlementRequest;
import io.korion.offlinepay.domain.status.SettlementStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class SettlementRequestRowMapper implements RowMapper<SettlementRequest> {

    @Override
    public SettlementRequest mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SettlementRequest(
                resultSet.getString("id"),
                resultSet.getString("batch_id"),
                resultSet.getString("collateral_id"),
                resultSet.getString("proof_id"),
                SettlementStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("reason_code"),
                resultSet.getBoolean("conflict_detected"),
                resultSet.getString("settlement_result"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
