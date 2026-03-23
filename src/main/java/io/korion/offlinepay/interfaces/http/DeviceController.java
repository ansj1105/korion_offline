package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.DeviceApplicationService;
import io.korion.offlinepay.interfaces.http.dto.RegisterDeviceRequest;
import io.korion.offlinepay.interfaces.http.dto.RevokeDeviceRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceApplicationService deviceApplicationService;

    public DeviceController(DeviceApplicationService deviceApplicationService) {
        this.deviceApplicationService = deviceApplicationService;
    }

    @PostMapping("/register")
    public Object register(@Valid @RequestBody RegisterDeviceRequest request) {
        return deviceApplicationService.registerDevice(new DeviceApplicationService.RegisterDeviceCommand(
                request.userId(),
                request.deviceId(),
                request.publicKey(),
                request.keyVersion() == null ? 1 : request.keyVersion(),
                request.metadata()
        ));
    }

    @PostMapping("/revoke")
    public Object revoke(@Valid @RequestBody RevokeDeviceRequest request) {
        return deviceApplicationService.revokeDevice(new DeviceApplicationService.RevokeDeviceCommand(
                request.deviceId(),
                request.keyVersion(),
                request.reason()
        ));
    }

    @PostMapping("/profile")
    public Object updateProfile(@Valid @RequestBody UpdateDeviceProfileRequest request) {
        return deviceApplicationService.updateDeviceProfile(new DeviceApplicationService.UpdateDeviceProfileCommand(
                request.userId(),
                request.deviceId(),
                request.metadata()
        ));
    }

    public record UpdateDeviceProfileRequest(
            long userId,
            String deviceId,
            java.util.Map<String, Object> metadata
    ) {}
}
