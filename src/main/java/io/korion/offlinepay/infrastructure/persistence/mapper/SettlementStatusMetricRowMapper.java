package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.SettlementStatusMetric;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class SettlementStatusMetricRowMapper implements RowMapper<SettlementStatusMetric> {

    @Override
    public SettlementStatusMetric mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SettlementStatusMetric(
                resultSet.getObject("bucket_at", OffsetDateTime.class),
                resultSet.getString("status"),
                resultSet.getLong("count")
        );
    }
}
