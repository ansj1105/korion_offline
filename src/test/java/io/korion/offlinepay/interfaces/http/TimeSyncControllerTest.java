package io.korion.offlinepay.interfaces.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeSyncControllerTest {

    @Test
    void returnsServerTimeWithClientTimezoneMetadata() {
        TimeSyncController controller = new TimeSyncController();

        TimeSyncController.TimeSyncResponse response = controller.sync("Asia/Seoul", 540);

        assertTrue(response.serverTime().endsWith("Z"));
        assertTrue(response.serverEpochMs() > 0);
        assertEquals("UTC", response.serverTimeZone());
        assertEquals("Asia/Seoul", response.clientTimeZone());
        assertEquals(540, response.clientTimeZoneOffsetMinutes());
    }

    @Test
    void fallsBackToUtcClientMetadata() {
        TimeSyncController controller = new TimeSyncController();

        TimeSyncController.TimeSyncResponse response = controller.sync(" ", null);

        assertEquals("UTC", response.clientTimeZone());
        assertEquals(0, response.clientTimeZoneOffsetMinutes());
    }
}
