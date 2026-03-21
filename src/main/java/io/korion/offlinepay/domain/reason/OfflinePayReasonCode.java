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
    public static final String SETTLED = "SETTLED";
}
