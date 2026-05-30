package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.StoreInfoRepository;
import io.korion.offlinepay.domain.model.StoreInfo;
import io.korion.offlinepay.infrastructure.persistence.mapper.StoreInfoRowMapper;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStoreInfoRepository implements StoreInfoRepository {

    private final JdbcClient jdbcClient;
    private final StoreInfoRowMapper storeInfoRowMapper;

    public JdbcStoreInfoRepository(
            JdbcClient jdbcClient,
            StoreInfoRowMapper storeInfoRowMapper
    ) {
        this.jdbcClient = jdbcClient;
        this.storeInfoRowMapper = storeInfoRowMapper;
    }

    @Override
    public Optional<StoreInfo> findByUserId(long userId) {
        String sql = QueryBuilder
                .select(
                        "store_info",
                        "user_id",
                        "store_name",
                        "description",
                        "address",
                        "contact_phone",
                        "business_hours_weekday",
                        "business_hours_weekend",
                        "business_hours_holiday",
                        "category",
                        "logo_image_url",
                        "background_image_url",
                        "created_at",
                        "updated_at"
                )
                .where("user_id", QueryBuilder.Op.EQ, ":userId")
                .build();
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .query(storeInfoRowMapper)
                .optional();
    }

    @Override
    public StoreInfo upsert(
            long userId,
            String storeName,
            String description,
            String address,
            String contactPhone,
            String businessHoursWeekday,
            String businessHoursWeekend,
            String businessHoursHoliday,
            String category,
            String logoImageUrl,
            String backgroundImageUrl
    ) {
        jdbcClient.sql("""
                INSERT INTO store_info (
                    user_id,
                    store_name,
                    description,
                    address,
                    contact_phone,
                    business_hours_weekday,
                    business_hours_weekend,
                    business_hours_holiday,
                    category,
                    logo_image_url,
                    background_image_url
                ) VALUES (
                    :userId,
                    :storeName,
                    :description,
                    :address,
                    :contactPhone,
                    :businessHoursWeekday,
                    :businessHoursWeekend,
                    :businessHoursHoliday,
                    :category,
                    :logoImageUrl,
                    :backgroundImageUrl
                )
                ON CONFLICT (user_id) DO UPDATE SET
                    store_name = EXCLUDED.store_name,
                    description = EXCLUDED.description,
                    address = EXCLUDED.address,
                    contact_phone = EXCLUDED.contact_phone,
                    business_hours_weekday = EXCLUDED.business_hours_weekday,
                    business_hours_weekend = EXCLUDED.business_hours_weekend,
                    business_hours_holiday = EXCLUDED.business_hours_holiday,
                    category = EXCLUDED.category,
                    logo_image_url = EXCLUDED.logo_image_url,
                    background_image_url = EXCLUDED.background_image_url,
                    updated_at = NOW()
                """)
                .param("userId", userId)
                .param("storeName", storeName)
                .param("description", description)
                .param("address", address)
                .param("contactPhone", contactPhone)
                .param("businessHoursWeekday", businessHoursWeekday)
                .param("businessHoursWeekend", businessHoursWeekend)
                .param("businessHoursHoliday", businessHoursHoliday)
                .param("category", category)
                .param("logoImageUrl", logoImageUrl)
                .param("backgroundImageUrl", backgroundImageUrl)
                .update();
        return findByUserId(userId).orElseThrow();
    }
}
