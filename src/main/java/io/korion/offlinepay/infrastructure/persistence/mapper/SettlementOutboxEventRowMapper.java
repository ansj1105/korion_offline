package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.SettlementOutboxEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class SettlementOutboxEventRowMapper implements RowMapper<SettlementOutboxEvent> {

    @Override
    public SettlementOutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SettlementOutboxEvent(
                rs.getObject("id").toString(),
                rs.getString("event_type"),
                rs.getString("status"),
                rs.getObject("batch_id") == null ? null : rs.getObject("batch_id").toString(),
                rs.getString("uploader_type"),
                rs.getString("uploader_device_id"),
                rs.getString("payload") == null ? "{}" : rs.getString("payload"),
                rs.getInt("attempts"),
                rs.getString("lock_owner"),
                rs.getObject("locked_at", java.time.OffsetDateTime.class),
                rs.getObject("processed_at", java.time.OffsetDateTime.class),
                rs.getObject("dead_lettered_at", java.time.OffsetDateTime.class),
                rs.getString("reason_code"),
                rs.getString("error_message"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
