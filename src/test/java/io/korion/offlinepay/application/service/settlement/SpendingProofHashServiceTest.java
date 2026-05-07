package io.korion.offlinepay.application.service.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SpendingProofHashServiceTest {

    private final SpendingProofHashService service = new SpendingProofHashService();

    @Test
    void computeNewStateHashUsesSixDecimalProofAmountFormat() {
        String hashFromWholeNumber = service.computeNewStateHash(
                "64155d36fc7f77f69ae784076df5055a259403e3e8d0a7fe9580c4ae2e5df93b",
                new BigDecimal("1"),
                20,
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                "nonce_050dada794e94b9b82974c33b2bdb41b"
        );
        String hashFromDbNumericScale = service.computeNewStateHash(
                "64155d36fc7f77f69ae784076df5055a259403e3e8d0a7fe9580c4ae2e5df93b",
                new BigDecimal("1.00000000"),
                20,
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                "nonce_050dada794e94b9b82974c33b2bdb41b"
        );

        assertEquals("5c7c0ab15fafb8fed9fddd5bdd63d2a4d3b932548ceb0f60846b72e1f8669705", hashFromWholeNumber);
        assertEquals(hashFromWholeNumber, hashFromDbNumericScale);
    }

    @Test
    void computeAcceptedNewStateHashesKeepsLegacyTwoDecimalProofAmountFormat() {
        var acceptedHashes = service.computeAcceptedNewStateHashes(
                "64155d36fc7f77f69ae784076df5055a259403e3e8d0a7fe9580c4ae2e5df93b",
                new BigDecimal("1.00000000"),
                20,
                "47ba2d8b-5b95-4510-8b23-007957e4fe46",
                "nonce_050dada794e94b9b82974c33b2bdb41b"
        );

        assertTrue(acceptedHashes.contains("5c7c0ab15fafb8fed9fddd5bdd63d2a4d3b932548ceb0f60846b72e1f8669705"));
        assertTrue(acceptedHashes.contains("b09218623be4bc048a4d36a8cb9e835fb0a5262a19a1b9a290eafb2f72dfe9d7"));
    }

    @Test
    void computeAcceptedNewStateHashesDoesNotAcceptLegacyHashWhenAmountNeedsMoreThanTwoDecimals() {
        var acceptedHashes = service.computeAcceptedNewStateHashes(
                "GENESIS",
                new BigDecimal("1.123456"),
                1,
                "device-1",
                "nonce-1"
        );

        assertEquals(1, acceptedHashes.size());
    }
}
