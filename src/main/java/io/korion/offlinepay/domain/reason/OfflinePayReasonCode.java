package io.korion.offlinepay.domain.reason;

public final class OfflinePayReasonCode {

    private OfflinePayReasonCode() {}

    public static final String INVALID_PROOF_SCHEMA = "INVALID_PROOF_SCHEMA";
    public static final String DEVICE_NOT_FOUND = "DEVICE_NOT_FOUND";
    public static final String INVALID_DEVICE_BINDING = "INVALID_DEVICE_BINDING";
    public static final String INVALID_DEVICE_SIGNATURE = "INVALID_DEVICE_SIGNATURE";
    public static final String DUPLICATE_SETTLEMENT = "DUPLICATE_SETTLEMENT";
    public static final String DEVICE_NOT_ACTIVE = "DEVICE_NOT_ACTIVE";
    public static final String KEY_VERSION_MISMATCH = "KEY_VERSION_MISMATCH";

    public static final String PAYMENT_MODE_REQUIRED = "PAYMENT_MODE_REQUIRED";
    public static final String IDLE_MODE_SUBMISSION_NOT_ALLOWED = "IDLE_MODE_SUBMISSION_NOT_ALLOWED";
    public static final String CONNECTION_TYPE_REQUIRED = "CONNECTION_TYPE_REQUIRED";
    public static final String PAYMENT_FLOW_REQUIRED = "PAYMENT_FLOW_REQUIRED";
    public static final String FAST_PAYMENT_REQUIRES_FAST_CONTACT = "FAST_PAYMENT_REQUIRES_FAST_CONTACT";

    public static final String AMOUNT_CONFLICT_DETECTED = "AMOUNT_CONFLICT_DETECTED";
    public static final String SENDER_AUTH_REQUIRED = "SENDER_AUTH_REQUIRED";
    public static final String LEDGER_EXECUTION_MODE_INVALID = "LEDGER_EXECUTION_MODE_INVALID";
    public static final String NETWORK_REQUIRED = "NETWORK_REQUIRED";
    public static final String NETWORK_NOT_ALLOWED = "NETWORK_NOT_ALLOWED";
    public static final String TOKEN_REQUIRED = "TOKEN_REQUIRED";
    public static final String TOKEN_MISMATCH = "TOKEN_MISMATCH";
    public static final String LOCAL_AVAILABLE_AMOUNT_REQUIRED = "LOCAL_AVAILABLE_AMOUNT_REQUIRED";
    public static final String LOCAL_AVAILABLE_AMOUNT_EXCEEDED = "LOCAL_AVAILABLE_AMOUNT_EXCEEDED";
    public static final String SERVER_AVAILABLE_AMOUNT_EXCEEDED = "SERVER_AVAILABLE_AMOUNT_EXCEEDED";
    public static final String PROOF_EXPIRED = "PROOF_EXPIRED";
    public static final String COLLATERAL_EXPIRED = "COLLATERAL_EXPIRED";
    public static final String COLLATERAL_LOCK_FAIL = "COLLATERAL_LOCK_FAIL";
    public static final String COLLATERAL_RELEASE_FAIL = "COLLATERAL_RELEASE_FAIL";
    public static final String POLICY_VERSION_MISMATCH = "POLICY_VERSION_MISMATCH";
    public static final String INSUFFICIENT_REMAINING_AMOUNT = "INSUFFICIENT_REMAINING_AMOUNT";
    public static final String INVALID_STATE_HASH = "INVALID_STATE_HASH";
    public static final String INVALID_GENESIS_COUNTER = "INVALID_GENESIS_COUNTER";
    public static final String INVALID_GENESIS_LINK = "INVALID_GENESIS_LINK";
    public static final String COUNTER_GAP = "COUNTER_GAP";
    public static final String INVALID_PREVIOUS_HASH = "INVALID_PREVIOUS_HASH";
    public static final String DUPLICATE_COUNTER = "DUPLICATE_COUNTER";
    public static final String DUPLICATE_NONCE = "DUPLICATE_NONCE";
    public static final String NFC_CONNECT_FAIL = "NFC_CONNECT_FAIL";
    public static final String BLE_SCAN_FAIL = "BLE_SCAN_FAIL";
    public static final String BLE_PAIR_FAIL = "BLE_PAIR_FAIL";
    public static final String QR_PARSE_FAIL = "QR_PARSE_FAIL";
    public static final String AUTH_BIOMETRIC_FAIL = "AUTH_BIOMETRIC_FAIL";
    public static final String AUTH_PIN_FAIL = "AUTH_PIN_FAIL";
    public static final String AUTH_CANCELLED = "AUTH_CANCELLED";
    public static final String PROOF_NOT_FOUND = "PROOF_NOT_FOUND";
    public static final String PROOF_TAMPERED = "PROOF_TAMPERED";
    public static final String ISSUED_PROOF_REQUIRED = "ISSUED_PROOF_REQUIRED";
    public static final String ISSUED_PROOF_NOT_FOUND = "ISSUED_PROOF_NOT_FOUND";
    public static final String ISSUED_PROOF_EXPIRED = "ISSUED_PROOF_EXPIRED";
    public static final String ISSUED_PROOF_STATUS_INVALID = "ISSUED_PROOF_STATUS_INVALID";
    public static final String ISSUED_PROOF_PAYLOAD_MISMATCH = "ISSUED_PROOF_PAYLOAD_MISMATCH";
    public static final String ISSUED_PROOF_SIGNATURE_INVALID = "ISSUED_PROOF_SIGNATURE_INVALID";
    public static final String ISSUED_PROOF_DEVICE_MISMATCH = "ISSUED_PROOF_DEVICE_MISMATCH";
    public static final String ISSUED_PROOF_AMOUNT_EXCEEDED = "ISSUED_PROOF_AMOUNT_EXCEEDED";
    public static final String ISSUED_PROOF_NONCE_MISMATCH = "ISSUED_PROOF_NONCE_MISMATCH";
    public static final String PAYLOAD_VOUCHER_MISMATCH = "PAYLOAD_VOUCHER_MISMATCH";
    public static final String PAYLOAD_DEVICE_MISMATCH = "PAYLOAD_DEVICE_MISMATCH";
    public static final String PAYLOAD_AMOUNT_MISMATCH = "PAYLOAD_AMOUNT_MISMATCH";
    public static final String PAYLOAD_COUNTER_MISMATCH = "PAYLOAD_COUNTER_MISMATCH";
    public static final String PAYLOAD_NONCE_MISMATCH = "PAYLOAD_NONCE_MISMATCH";
    public static final String PAYLOAD_HASH_MISMATCH = "PAYLOAD_HASH_MISMATCH";
    public static final String PAYLOAD_SIGNATURE_MISMATCH = "PAYLOAD_SIGNATURE_MISMATCH";
    public static final String PAYLOAD_TIMESTAMP_MISMATCH = "PAYLOAD_TIMESTAMP_MISMATCH";
    public static final String PAYLOAD_EXPIRY_MISMATCH = "PAYLOAD_EXPIRY_MISMATCH";
    public static final String PAYLOAD_REQUIRED_FIELD_MISSING = "PAYLOAD_REQUIRED_FIELD_MISSING";
    public static final String PAYLOAD_BUILD_FAIL = "PAYLOAD_BUILD_FAIL";
    public static final String SEND_TIMEOUT = "SEND_TIMEOUT";
    public static final String SEND_INTERRUPTED = "SEND_INTERRUPTED";
    public static final String RECEIVE_REJECTED = "RECEIVE_REJECTED";
    public static final String LOCAL_QUEUE_SAVE_FAIL = "LOCAL_QUEUE_SAVE_FAIL";
    public static final String BATCH_SYNC_FAIL = "BATCH_SYNC_FAIL";
    public static final String LEDGER_CIRCUIT_OPEN = "LEDGER_CIRCUIT_OPEN";
    public static final String LEDGER_SYNC_FAIL = "LEDGER_SYNC_FAIL";
    public static final String HISTORY_CIRCUIT_OPEN = "HISTORY_CIRCUIT_OPEN";
    public static final String HISTORY_SYNC_FAIL = "HISTORY_SYNC_FAIL";
    public static final String SERVER_VALIDATION_FAIL = "SERVER_VALIDATION_FAIL";
    public static final String SETTLEMENT_FAIL = "SETTLEMENT_FAIL";
    public static final String PARTIAL_SETTLEMENT = "PARTIAL_SETTLEMENT";
    public static final String UNKNOWN_CONFLICT = "UNKNOWN_CONFLICT";
    public static final String SETTLED = "SETTLED";
}
