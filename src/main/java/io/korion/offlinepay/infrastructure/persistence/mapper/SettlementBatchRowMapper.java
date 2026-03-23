package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.SettlementBatch;
import io.korion.offlinepay.domain.status.SettlementBatchStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class SettlementBatchRowMapper implements RowMapper<SettlementBatch> {

    @Override
    public SettlementBatch mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SettlementBatch(
                resultSet.getString("id"),
                resultSet.getString("source_device_id"),
                resultSet.getString("idempotency_key"),
                SettlementBatchStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("last_reason_code"),
                resultSet.getInt("proofs_count"),
                resultSet.getString("summary"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
