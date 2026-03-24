package io.korion.offlinepay.domain.policy;

import io.korion.offlinepay.domain.reason.OfflinePayReasonCode;
import java.time.Duration;

public final class OfflineFailurePolicy {

    private OfflineFailurePolicy() {}

    public static OfflineFailureClass classify(String reasonCode, String errorMessage) {
        String normalizedReason = normalize(reasonCode);
        String normalizedError = normalize(errorMessage);

        if (containsAny(normalizedError, "401", "UNAUTHORIZED", "INVALID API KEY")) {
            return OfflineFailureClass.AUTH;
        }

        if (containsAny(
                normalizedReason,
                OfflinePayReasonCode.DUPLICATE_SETTLEMENT,
                OfflinePayReasonCode.DUPLICATE_COUNTER,
                OfflinePayReasonCode.DUPLICATE_NONCE,
                OfflinePayReasonCode.UNKNOWN_CONFLICT
        ) || containsAny(normalizedError, "DUPLICATE", "CONFLICT")) {
            return OfflineFailureClass.CONFLICT;
        }

        if (containsAny(
                normalizedReason,
                OfflinePayReasonCode.PARTIAL_SETTLEMENT,
                OfflinePayReasonCode.LEDGER_SYNC_FAIL,
                OfflinePayReasonCode.HISTORY_SYNC_FAIL,
                OfflinePayReasonCode.LEDGER_CIRCUIT_OPEN,
                OfflinePayReasonCode.HISTORY_CIRCUIT_OPEN
        )) {
            return OfflineFailureClass.PARTIAL;
        }

        if (containsAny(
                normalizedReason,
                OfflinePayReasonCode.INVALID_PROOF_SCHEMA,
                OfflinePayReasonCode.DEVICE_NOT_FOUND,
                OfflinePayReasonCode.INVALID_DEVICE_BINDING,
                OfflinePayReasonCode.INVALID_DEVICE_SIGNATURE,
                OfflinePayReasonCode.DEVICE_NOT_ACTIVE,
                OfflinePayReasonCode.KEY_VERSION_MISMATCH,
                OfflinePayReasonCode.PAYMENT_MODE_REQUIRED,
                OfflinePayReasonCode.IDLE_MODE_SUBMISSION_NOT_ALLOWED,
                OfflinePayReasonCode.CONNECTION_TYPE_REQUIRED,
                OfflinePayReasonCode.PAYMENT_FLOW_REQUIRED,
                OfflinePayReasonCode.FAST_PAYMENT_REQUIRES_FAST_CONTACT,
                OfflinePayReasonCode.AMOUNT_CONFLICT_DETECTED,
                OfflinePayReasonCode.SENDER_AUTH_REQUIRED,
                OfflinePayReasonCode.LEDGER_EXECUTION_MODE_INVALID,
                OfflinePayReasonCode.NETWORK_REQUIRED,
                OfflinePayReasonCode.NETWORK_NOT_ALLOWED,
                OfflinePayReasonCode.TOKEN_REQUIRED,
                OfflinePayReasonCode.TOKEN_MISMATCH,
                OfflinePayReasonCode.LOCAL_AVAILABLE_AMOUNT_REQUIRED,
                OfflinePayReasonCode.LOCAL_AVAILABLE_AMOUNT_EXCEEDED,
                OfflinePayReasonCode.SERVER_AVAILABLE_AMOUNT_EXCEEDED,
                OfflinePayReasonCode.PROOF_EXPIRED,
                OfflinePayReasonCode.COLLATERAL_EXPIRED,
                OfflinePayReasonCode.PROOF_NOT_FOUND,
                OfflinePayReasonCode.PROOF_TAMPERED,
                OfflinePayReasonCode.ISSUED_PROOF_REQUIRED,
                OfflinePayReasonCode.ISSUED_PROOF_NOT_FOUND,
                OfflinePayReasonCode.ISSUED_PROOF_EXPIRED,
                OfflinePayReasonCode.ISSUED_PROOF_STATUS_INVALID,
                OfflinePayReasonCode.ISSUED_PROOF_PAYLOAD_MISMATCH,
                OfflinePayReasonCode.ISSUED_PROOF_SIGNATURE_INVALID,
                OfflinePayReasonCode.ISSUED_PROOF_DEVICE_MISMATCH,
                OfflinePayReasonCode.ISSUED_PROOF_AMOUNT_EXCEEDED,
                OfflinePayReasonCode.ISSUED_PROOF_NONCE_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_REQUIRED_FIELD_MISSING,
                OfflinePayReasonCode.PAYLOAD_VOUCHER_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_DEVICE_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_AMOUNT_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_COUNTER_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_NONCE_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_HASH_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_SIGNATURE_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_TIMESTAMP_MISMATCH,
                OfflinePayReasonCode.PAYLOAD_EXPIRY_MISMATCH
        ) || containsAny(
                normalizedError,
                "INSUFFICIENT_BALANCE",
                "VALIDATION_ERROR",
                "400 BAD REQUEST",
                "404 NOT FOUND",
                "NO RELEASABLE",
                "EXCEEDS REMAINING"
        )) {
            return OfflineFailureClass.BUSINESS;
        }

        if (containsAny(
                normalizedReason,
                OfflinePayReasonCode.NFC_CONNECT_FAIL,
                OfflinePayReasonCode.BLE_SCAN_FAIL,
                OfflinePayReasonCode.BLE_PAIR_FAIL,
                OfflinePayReasonCode.QR_PARSE_FAIL,
                OfflinePayReasonCode.SEND_TIMEOUT,
                OfflinePayReasonCode.SEND_INTERRUPTED,
                OfflinePayReasonCode.LOCAL_QUEUE_SAVE_FAIL
        ) || containsAny(
                normalizedError,
                "FETCH FAILED",
                "FAILED TO FETCH",
                "TIMEOUT",
                "ECONN",
                "NETWORK",
                "502",
                "503",
                "504",
                "CIRCUIT IS OPEN"
        )) {
            return OfflineFailureClass.TRANSPORT;
        }

        return OfflineFailureClass.SYSTEM;
    }

    public static boolean isRetryable(OfflineFailureClass failureClass) {
        return failureClass == OfflineFailureClass.TRANSPORT
                || failureClass == OfflineFailureClass.PARTIAL
                || failureClass == OfflineFailureClass.SYSTEM;
    }

    public static int maxAutoRetryAttempts(OfflineFailureClass failureClass) {
        return switch (failureClass) {
            case TRANSPORT -> 10;
            case PARTIAL -> 6;
            case SYSTEM -> 3;
            case AUTH -> 2;
            case BUSINESS, CONFLICT -> 0;
        };
    }

    public static Duration nextRetryDelay(OfflineFailureClass failureClass, int retryCount) {
        int[] minutes = switch (failureClass) {
            case TRANSPORT -> new int[] {1, 5, 15, 60, 360};
            case PARTIAL -> new int[] {5, 15, 60, 360};
            case SYSTEM -> new int[] {5, 15, 60};
            case AUTH -> new int[] {1, 5};
            case BUSINESS, CONFLICT -> new int[] {0};
        };
        int index = Math.max(0, Math.min(retryCount, minutes.length - 1));
        return Duration.ofMinutes(minutes[index]);
    }

    public static String adminAction(OfflineFailureClass failureClass) {
        return switch (failureClass) {
            case TRANSPORT -> "BULK_RETRY_ALLOWED";
            case PARTIAL -> "RECONCILIATION_RETRY_PRIORITY";
            case SYSTEM -> "OPERATOR_REVIEW_REQUIRED";
            case AUTH -> "SECRET_OR_BINDING_REVIEW";
            case BUSINESS -> "MANUAL_FIX_THEN_RETRY";
            case CONFLICT -> "RESOLVE_OR_CLOSE_ONLY";
        };
    }

    private static boolean containsAny(String value, String... patterns) {
        for (String pattern : patterns) {
            if (value.contains(normalize(pattern))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
