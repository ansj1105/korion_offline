package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;

public record RevokeDeviceRequest(
        @NotBlank String deviceId,
        Integer keyVersion,
        String reason
) {}

