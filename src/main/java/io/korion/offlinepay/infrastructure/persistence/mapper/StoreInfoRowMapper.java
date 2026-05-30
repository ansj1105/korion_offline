package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.StoreInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class StoreInfoRowMapper implements RowMapper<StoreInfo> {

    @Override
    public StoreInfo mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new StoreInfo(
                resultSet.getLong("user_id"),
                resultSet.getString("store_name"),
                resultSet.getString("description"),
                resultSet.getString("address"),
                resultSet.getString("contact_phone"),
                new StoreInfo.BusinessHours(
                        resultSet.getString("business_hours_weekday"),
                        resultSet.getString("business_hours_weekend"),
                        resultSet.getString("business_hours_holiday")
                ),
                resultSet.getString("category"),
                resultSet.getString("logo_image_url"),
                resultSet.getString("background_image_url"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
