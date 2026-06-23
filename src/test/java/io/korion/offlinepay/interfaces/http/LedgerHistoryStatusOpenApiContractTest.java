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

    @Test
    void settlementRequestDetailStatusUsesConfirmedBeforeReceiverWalletSettlement() throws Exception {
        String api = Files.readString(Path.of("docs/api.md"));

        int schemaIndex = api.indexOf("SettlementRequestDetailResponse:");
        assertTrue(schemaIndex >= 0, "SettlementRequestDetailResponse schema must be documented");

        int statusIndex = api.indexOf("status:", schemaIndex);
        assertTrue(statusIndex >= 0, "SettlementRequestDetailResponse.status must be documented");

        int proofIdIndex = api.indexOf("proofId:", schemaIndex);
        assertTrue(proofIdIndex >= 0, "SettlementRequestDetailResponse.proofId must be documented");
        assertTrue(proofIdIndex < statusIndex, "SettlementRequestDetailResponse.proofId must be documented before status");

        int enumIndex = api.indexOf("enum:", statusIndex);
        assertTrue(enumIndex >= 0, "SettlementRequestDetailResponse.status enum must be documented");

        int nextFieldIndex = api.indexOf("\n        reasonCode:", enumIndex);
        assertTrue(nextFieldIndex > enumIndex, "SettlementRequestDetailResponse.status enum block must end before reasonCode field");

        String statusBlock = api.substring(statusIndex, nextFieldIndex);

        assertTrue(statusBlock.contains("enum: [PENDING, CONFIRMED, SETTLED, FAILED]"));
        assertTrue(statusBlock.contains("CONFIRMED means server validation succeeded but receiver wallet/history settlement is not finalized"));
        assertTrue(statusBlock.contains("SETTLED means receiver wallet/history settlement completed"));
        assertFalse(statusBlock.contains("COMPLETED,"));
        assertFalse(statusBlock.contains(", COMPLETED"));
    }

    @Test
    void finalizeSettlementStatusUsesConfirmedForServerValidationCompletion() throws Exception {
        String api = Files.readString(Path.of("docs/api.md"));

        int schemaIndex = api.indexOf("FinalizeSettlementResponse:");
        assertTrue(schemaIndex >= 0, "FinalizeSettlementResponse schema must be documented");

        int statusIndex = api.indexOf("status:", schemaIndex);
        assertTrue(statusIndex >= 0, "FinalizeSettlementResponse.status must be documented");

        int enumIndex = api.indexOf("enum:", statusIndex);
        assertTrue(enumIndex >= 0, "FinalizeSettlementResponse.status enum must be documented");

        int nextFieldIndex = api.indexOf("\n          description:", enumIndex);
        assertTrue(nextFieldIndex > enumIndex, "FinalizeSettlementResponse.status enum block must end before description field");

        String statusBlock = api.substring(statusIndex, nextFieldIndex);

        assertTrue(statusBlock.contains("enum: [PENDING, CONFIRMED, FAILED]"));
        assertFalse(statusBlock.contains("SETTLED"));
        assertFalse(statusBlock.contains("COMPLETED"));
    }
}
