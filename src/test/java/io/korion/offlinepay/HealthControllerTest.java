package io.korion.offlinepay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.korion.offlinepay.config.AppProperties;
import io.korion.offlinepay.interfaces.http.HealthController;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void returnsHealthPayload() {
        HealthController controller = new HealthController(new AppProperties(
                "USDT",
                24,
                20,
                1000,
                new AppProperties.ProofIssuer("test-proof-issuer", "", ""),
                new AppProperties.CoinManage("http://localhost:3000", "test-key", 5000),
                new AppProperties.FoxCoin("http://localhost:3101", "test-key", 5000),
                new AppProperties.Redis(
                        "offlinepay",
                        "stream:settlement:requested",
                        "stream:settlement:result",
                        "stream:settlement:conflict",
                        "stream:settlement:dead-letter",
                        "stream:collateral:requested",
                        "stream:collateral:result",
                        "offlinepay:settlement-group"
                ),
                new AppProperties.Worker(false, "test-worker", 60000, 3)
        ));

        Map<String, Object> response = controller.health();

        assertEquals(true, response.get("ok"));
        assertEquals("offline-pay-backend", response.get("service"));
        assertTrue(response.containsKey("now"));
    }
}
