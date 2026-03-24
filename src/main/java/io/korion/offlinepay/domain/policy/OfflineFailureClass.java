package io.korion.offlinepay.domain.policy;

public enum OfflineFailureClass {
    TRANSPORT,
    AUTH,
    BUSINESS,
    PARTIAL,
    CONFLICT,
    SYSTEM
}
