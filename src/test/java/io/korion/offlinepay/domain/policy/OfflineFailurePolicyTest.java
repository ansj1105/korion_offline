package io.korion.offlinepay.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import org.junit.jupiter.api.Test;

class OfflineFailurePolicyTest {

    @Test
    void classifiesCryptographicFailuresAsNonRetryableBusinessFailures() {
        assertTerminalBusiness(OfflinePayReasonCode.INVALID_DEVICE_SIGNATURE);
        assertTerminalBusiness(OfflinePayReasonCode.PAYLOAD_SIGNATURE_MISMATCH);
        assertTerminalBusiness(OfflinePayReasonCode.PAYLOAD_NONCE_MISMATCH);
    }

    @Test
    void classifiesDuplicateNonceAsNonRetryableConflict() {
        OfflineFailureClass failureClass = OfflineFailurePolicy.classify(OfflinePayReasonCode.DUPLICATE_NONCE, "");

        assertEquals(OfflineFailureClass.CONFLICT, failureClass);
        assertFalse(OfflineFailurePolicy.isRetryable(failureClass));
    }

    @Test
    void classifiesOfflineTimingAndReceiverDelayAsRetryableTemporalFailures() {
        assertRetryableTemporal(OfflinePayReasonCode.PROOF_EXPIRED);
        assertRetryableTemporal(OfflinePayReasonCode.RECEIVER_CONFIRMATION_EXPIRED);
        assertRetryableTemporal(OfflinePayReasonCode.OFFLINE_ESTIMATED_TIME_STALE);
        assertRetryableTemporal(OfflinePayReasonCode.RECEIVER_EVIDENCE_REQUIRED);
        assertRetryableTemporal(null, "WAITING_SENDER_PROOF_TIMEOUT");
    }

    private void assertTerminalBusiness(String reasonCode) {
        OfflineFailureClass failureClass = OfflineFailurePolicy.classify(reasonCode, "");

        assertEquals(OfflineFailureClass.BUSINESS, failureClass);
        assertFalse(OfflineFailurePolicy.isRetryable(failureClass));
    }

    private void assertRetryableTemporal(String reasonCode) {
        assertRetryableTemporal(reasonCode, "");
    }

    private void assertRetryableTemporal(String reasonCode, String errorMessage) {
        OfflineFailureClass failureClass = OfflineFailurePolicy.classify(reasonCode, errorMessage);

        assertEquals(OfflineFailureClass.TEMPORAL, failureClass);
        assertTrue(OfflineFailurePolicy.isRetryable(failureClass));
    }
}
