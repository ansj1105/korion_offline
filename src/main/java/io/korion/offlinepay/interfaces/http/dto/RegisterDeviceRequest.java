package io.korion.offlinepay.interfaces.http.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record RegisterDeviceRequest(
        @NotNull @Min(1) Long userId,
        @NotBlank String deviceId,
        @NotBlank String publicKey,
        @Min(1) Integer keyVersion,
        Map<String, Object> metadata
) {}

