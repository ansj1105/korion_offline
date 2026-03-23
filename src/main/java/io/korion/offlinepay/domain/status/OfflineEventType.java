package io.korion.offlinepay.domain.status;

public enum OfflineEventType {
    REQUEST_SENT,
    REQUEST_RECEIVED,
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    REQUEST_CANCELLED,
    SYNC_FAILED,
    SETTLEMENT_FAILED,
    TRANSPORT_FAILED
}
