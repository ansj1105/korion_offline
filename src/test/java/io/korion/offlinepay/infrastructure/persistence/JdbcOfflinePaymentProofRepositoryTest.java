package io.korion.offlinepay.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;

class JdbcOfflinePaymentProofRepositoryTest {

    @Test
    void senderOfflineTxSequenceLookupSqlAvoidsJsonbQuestionMarkOperator() {
        String sql = JdbcOfflinePaymentProofRepository.senderOfflineTxSequenceLookupSql();

        assertTrue(sql.contains("jsonb_exists(raw_payload, 'offlineTxSequence')"));
        assertFalse(sql.contains("raw_payload ? 'offlineTxSequence'"));
        assertTrue(sql.contains(":senderDeviceId"));
        assertTrue(sql.contains(":offlineTxSequence"));
        assertDoesNotThrow(() -> NamedParameterUtils.parseSqlStatement(sql));
    }

    @Test
    void latestSequenceAnchorIncludesNonFinancialConsumedCounterMarkers() {
        String sql = JdbcOfflinePaymentProofRepository.latestSequenceAnchorLookupSql();

        assertTrue(sql.contains("offline_payment_proofs.sequence_anchor_at IS NOT NULL"));
        assertTrue(sql.contains("offline_payment_proofs.status = 'SETTLED'"));
        assertTrue(sql.contains("offline_payment_proofs.verified_at IS NOT NULL"));
        assertTrue(sql.contains("offline_payment_proofs.reason_code = 'COUNTER_GAP'"));
        assertTrue(sql.contains("offline_payment_proofs.reason_code = 'INVALID_GENESIS_COUNTER'"));
        assertTrue(sql.contains("offline_payment_proofs.reason_code = 'SENDER_AUTH_NOT_COMPLETED'"));
        assertTrue(sql.contains(":senderDeviceId"));
        assertDoesNotThrow(() -> NamedParameterUtils.parseSqlStatement(sql));
    }

    @Test
    void finalizedReceivedUnsettledCandidatesIncludeHonoredSettlements() {
        String sql = JdbcOfflinePaymentProofRepository.finalizedReceivedUnsettledCandidatesSql(25);

        assertTrue(sql.contains("offline_payment_proofs.received_unsettled_amount > 0"));
        assertTrue(sql.contains("settlement_requests.status = 'SETTLED'"));
        assertTrue(sql.contains("settlement_requests.settlement_result ->> 'financiallyHonored'"));
        assertTrue(sql.contains("settlements.status = 'SETTLED'"));
        assertTrue(sql.contains("settlements.detail ->> 'financiallyHonored'"));
        assertDoesNotThrow(() -> NamedParameterUtils.parseSqlStatement(sql));
    }

    @Test
    void attachSenderProofPromotesPolicyAndHybridTimeFields() {
        String sql = JdbcOfflinePaymentProofRepository.attachSenderProofSql();

        assertTrue(sql.contains("matchedSenderProof"));
        assertTrue(sql.contains("uiMode"));
        assertTrue(sql.contains("paymentFlow"));
        assertTrue(sql.contains("connectionType"));
        assertTrue(sql.contains("availableAmount"));
        assertTrue(sql.contains("senderAuthRequired"));
        assertTrue(sql.contains("offlineTxSequence"));
        assertTrue(sql.contains("lastServerSyncTime"));
        assertTrue(sql.contains("estimatedServerTime"));
        assertTrue(sql.contains("elapsedTimeMs"));
        assertTrue(sql.contains("canonical_payload"));
        assertTrue(sql.contains("localBlockLastServerSyncTime"));
        assertDoesNotThrow(() -> NamedParameterUtils.parseSqlStatement(sql));
    }
}
