package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.SettlementConflictMetric;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class SettlementConflictMetricRowMapper implements RowMapper<SettlementConflictMetric> {

    @Override
    public SettlementConflictMetric mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SettlementConflictMetric(
                resultSet.getObject("bucket_at", OffsetDateTime.class),
                resultSet.getLong("count")
        );
    }
}
