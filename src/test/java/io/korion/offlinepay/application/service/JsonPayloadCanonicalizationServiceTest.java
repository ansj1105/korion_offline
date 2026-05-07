package io.korion.offlinepay.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonPayloadCanonicalizationServiceTest {

    private final JsonPayloadCanonicalizationService service = new JsonPayloadCanonicalizationService(
            new JsonService(new ObjectMapper())
    );

    @Test
    void canonicalizeSortsObjectKeysRecursively() {
        String canonical = service.canonicalize("""
                {
                  "z": 1,
                  "a": {
                    "b": 2,
                    "a": 1
                  }
                }
                """);

        assertEquals("{\"a\":{\"a\":1,\"b\":2},\"z\":1}", canonical);
    }

    @Test
    void sameJsonIgnoresWhitespaceAndObjectKeyOrder() {
        assertTrue(service.sameJson(
                "{\"b\":2,\"a\":{\"d\":4,\"c\":3}}",
                """
                        {
                          "a": {
                            "c": 3,
                            "d": 4
                          },
                          "b": 2
                        }
                        """
        ));
    }

    @Test
    void sameJsonRejectsBlankPayloads() {
        assertFalse(service.sameJson("", "{}"));
    }
}
