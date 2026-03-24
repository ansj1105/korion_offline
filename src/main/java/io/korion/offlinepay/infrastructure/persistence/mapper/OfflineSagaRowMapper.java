package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.OfflineSaga;
import io.korion.offlinepay.domain.status.OfflineSagaStatus;
import io.korion.offlinepay.domain.status.OfflineSagaType;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class OfflineSagaRowMapper implements RowMapper<OfflineSaga> {

    @Override
    public OfflineSaga mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OfflineSaga(
                rs.getObject("id").toString(),
                OfflineSagaType.valueOf(rs.getString("saga_type")),
                rs.getString("reference_id"),
                OfflineSagaStatus.valueOf(rs.getString("status")),
                rs.getString("current_step"),
                rs.getString("last_reason_code"),
                rs.getString("payload_json") == null ? "{}" : rs.getString("payload_json"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
