package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.ReconciliationCase;
import io.korion.offlinepay.domain.status.ReconciliationCaseStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationCaseRowMapper implements RowMapper<ReconciliationCase> {

    @Override
    public ReconciliationCase mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ReconciliationCase(
                resultSet.getString("id"),
                resultSet.getString("settlement_id"),
                resultSet.getString("batch_id"),
                resultSet.getString("proof_id"),
                resultSet.getString("voucher_id"),
                resultSet.getString("case_type"),
                ReconciliationCaseStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("reason_code"),
                resultSet.getString("detail"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("resolved_at", OffsetDateTime.class)
        );
    }
}
