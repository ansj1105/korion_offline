package io.korion.offlinepay.application.port;

import io.korion.offlinepay.domain.model.StoreInfo;
import java.util.Optional;

public interface StoreInfoRepository {

    Optional<StoreInfo> findByUserId(long userId);

    StoreInfo upsert(
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
    );
}
