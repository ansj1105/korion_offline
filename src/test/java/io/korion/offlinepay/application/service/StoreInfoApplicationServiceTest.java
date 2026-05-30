package io.korion.offlinepay.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.korion.offlinepay.application.port.StoreInfoRepository;
import io.korion.offlinepay.domain.model.StoreInfo;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StoreInfoApplicationServiceTest {

    @Test
    void upsertsNormalizedStoreInfo() {
        InMemoryStoreInfoRepository repository = new InMemoryStoreInfoRepository();
        StoreInfoApplicationService service = new StoreInfoApplicationService(repository);

        StoreInfo saved = service.upsertStoreInfo(new StoreInfoApplicationService.UpsertStoreInfoCommand(
                1L,
                "  Cafe Korion  ",
                "  Coffee  ",
                "  Seoul  ",
                " +821012345678 ",
                new StoreInfo.BusinessHours(" 09:00 ~ 18:00 ", " 10:00 ~ 17:00 ", ""),
                " cafe ",
                "",
                ""
        ));

        assertThat(saved.userId()).isEqualTo(1L);
        assertThat(saved.storeName()).isEqualTo("Cafe Korion");
        assertThat(saved.description()).isEqualTo("Coffee");
        assertThat(saved.businessHours().weekday()).isEqualTo("09:00 ~ 18:00");
        assertThat(service.getStoreInfo(1L)).contains(saved);
    }

    @Test
    void rejectsBlankStoreName() {
        StoreInfoApplicationService service = new StoreInfoApplicationService(new InMemoryStoreInfoRepository());

        assertThatThrownBy(() -> service.upsertStoreInfo(new StoreInfoApplicationService.UpsertStoreInfoCommand(
                1L,
                " ",
                "",
                "",
                "",
                new StoreInfo.BusinessHours("", "", ""),
                "etc",
                "",
                ""
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storeName");
    }

    private static class InMemoryStoreInfoRepository implements StoreInfoRepository {
        private StoreInfo snapshot;

        @Override
        public Optional<StoreInfo> findByUserId(long userId) {
            return Optional.ofNullable(snapshot).filter(item -> item.userId() == userId);
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
            OffsetDateTime now = OffsetDateTime.parse("2026-05-30T00:00:00Z");
            snapshot = new StoreInfo(
                    userId,
                    storeName,
                    description,
                    address,
                    contactPhone,
                    new StoreInfo.BusinessHours(businessHoursWeekday, businessHoursWeekend, businessHoursHoliday),
                    category,
                    logoImageUrl,
                    backgroundImageUrl,
                    now,
                    now
            );
            return snapshot;
        }
    }
}
