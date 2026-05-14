package io.korion.offlinepay.application.port;

public interface FoxCoinStoreProductPolicyPort {

    StoreProductPolicy getStoreProductPolicy(long userId);

    record StoreProductPolicy(
            long userId,
            int level,
            int storeProductLimit,
            int dailyMaxAds,
            boolean profilePhotoEnabled
    ) {}
}
