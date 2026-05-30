package io.korion.offlinepay.application.service;

import io.korion.offlinepay.application.port.StoreInfoRepository;
import io.korion.offlinepay.domain.model.StoreInfo;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreInfoApplicationService {

    private static final int MAX_STORE_NAME_LENGTH = 120;
    private static final int MAX_TEXT_LENGTH = 4_000;
    private static final int MAX_SHORT_TEXT_LENGTH = 120;

    private final StoreInfoRepository storeInfoRepository;

    public StoreInfoApplicationService(StoreInfoRepository storeInfoRepository) {
        this.storeInfoRepository = storeInfoRepository;
    }

    @Transactional(readOnly = true)
    public Optional<StoreInfo> getStoreInfo(long userId) {
        validateUserId(userId);
        return storeInfoRepository.findByUserId(userId);
    }

    @Transactional
    public StoreInfo upsertStoreInfo(UpsertStoreInfoCommand command) {
        validateUserId(command.userId());
        String storeName = normalizeRequired(command.storeName(), "storeName", MAX_STORE_NAME_LENGTH);
        StoreInfo.BusinessHours hours = command.businessHours() == null
                ? new StoreInfo.BusinessHours("", "", "")
                : command.businessHours();

        return storeInfoRepository.upsert(
                command.userId(),
                storeName,
                normalizeOptional(command.description(), MAX_TEXT_LENGTH),
                normalizeOptional(command.address(), MAX_TEXT_LENGTH),
                normalizeOptional(command.contactPhone(), MAX_SHORT_TEXT_LENGTH),
                normalizeOptional(hours.weekday(), MAX_SHORT_TEXT_LENGTH),
                normalizeOptional(hours.weekend(), MAX_SHORT_TEXT_LENGTH),
                normalizeOptional(hours.holiday(), MAX_SHORT_TEXT_LENGTH),
                normalizeOptional(command.category(), MAX_SHORT_TEXT_LENGTH).isBlank()
                        ? "etc"
                        : normalizeOptional(command.category(), MAX_SHORT_TEXT_LENGTH),
                normalizeOptional(command.logoImageUrl(), MAX_TEXT_LENGTH),
                normalizeOptional(command.backgroundImageUrl(), MAX_TEXT_LENGTH)
        );
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    public record UpsertStoreInfoCommand(
            long userId,
            String storeName,
            String description,
            String address,
            String contactPhone,
            StoreInfo.BusinessHours businessHours,
            String category,
            String logoImageUrl,
            String backgroundImageUrl
    ) {}
}
