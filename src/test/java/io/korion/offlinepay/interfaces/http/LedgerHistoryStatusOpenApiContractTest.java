package io.korion.offlinepay.interfaces.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LedgerHistoryStatusOpenApiContractTest {

    @Test
    void ledgerHistoryStatusCodePublicEnumDoesNotExposeCompleted() throws Exception {
        String api = Files.readString(Path.of("docs/api.md"));

        int schemaIndex = api.indexOf("LedgerHistoryItem:");
        assertTrue(schemaIndex >= 0, "LedgerHistoryItem schema must be documented");

        int statusCodeIndex = api.indexOf("statusCode:", schemaIndex);
        assertTrue(statusCodeIndex >= 0, "LedgerHistoryItem.statusCode must be documented");

        int enumIndex = api.indexOf("enum:", statusCodeIndex);
        assertTrue(enumIndex >= 0, "LedgerHistoryItem.statusCode enum must be documented");

        int nextFieldIndex = api.indexOf("\n        network:", enumIndex);
        assertTrue(nextFieldIndex > enumIndex, "LedgerHistoryItem.statusCode enum block must end before network field");

        String statusCodeBlock = api.substring(statusCodeIndex, nextFieldIndex);

        assertTrue(statusCodeBlock.contains("enum: [PENDING, CONFIRMED, SETTLED, FAILED, EXPIRED, REJECTED, LOCKED]"));
        assertTrue(statusCodeBlock.contains("new API responses must use CONFIRMED"));
        assertFalse(statusCodeBlock.contains("COMPLETED,"));
        assertFalse(statusCodeBlock.contains(", COMPLETED"));
    }
}
