package io.korion.offlinepay.contracts.internal;

public record InternalAckResponseContract(
        String status,
        String message
) {}
