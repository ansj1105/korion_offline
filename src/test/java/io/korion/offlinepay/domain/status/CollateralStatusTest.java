package io.korion.offlinepay.domain.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CollateralStatusTest {

    @Test
    void mapsLegacyActiveStatusToLocked() {
        assertEquals(CollateralStatus.LOCKED, CollateralStatus.fromPersistence("ACTIVE"));
    }

    @Test
    void mapsCurrentStatusesCaseInsensitively() {
        assertEquals(CollateralStatus.RELEASED, CollateralStatus.fromPersistence("released"));
    }
}
