package io.korion.offlinepay.infrastructure.persistence.mapper;

import io.korion.offlinepay.domain.model.StoreProduct;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class StoreProductRowMapper implements RowMapper<StoreProduct> {

    @Override
    public StoreProduct mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new StoreProduct(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("image_url"),
                resultSet.getBigDecimal("price"),
                resultSet.getInt("stock_current"),
                resultSet.getInt("stock_total"),
                resultSet.getBoolean("visible"),
                resultSet.getInt("sort_order"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
