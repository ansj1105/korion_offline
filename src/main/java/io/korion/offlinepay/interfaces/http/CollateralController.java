package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.CollateralApplicationService;
import io.korion.offlinepay.interfaces.http.dto.CreateCollateralRequest;
import io.korion.offlinepay.interfaces.http.dto.ReleaseCollateralRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collateral")
public class CollateralController {

    private final CollateralApplicationService collateralApplicationService;

    public CollateralController(CollateralApplicationService collateralApplicationService) {
        this.collateralApplicationService = collateralApplicationService;
    }

    @PostMapping
    public Object create(@Valid @RequestBody CreateCollateralRequest request) {
        return collateralApplicationService.createCollateral(new CollateralApplicationService.CreateCollateralCommand(
                request.userId(),
                request.deviceId(),
                request.amount(),
                request.assetCode(),
                request.initialStateRoot(),
                request.policyVersion(),
                request.metadata()
        ));
    }

    @GetMapping("/{collateralId}")
    public Object detail(@PathVariable String collateralId) {
        return collateralApplicationService.getCollateral(collateralId);
    }

    @PostMapping("/{collateralId}/release")
    public Object release(@PathVariable String collateralId, @Valid @RequestBody ReleaseCollateralRequest request) {
        return collateralApplicationService.releaseCollateral(
                collateralId,
                new CollateralApplicationService.ReleaseCollateralCommand(
                        request.userId(),
                        request.deviceId(),
                        request.reason(),
                        request.metadata()
                )
        );
    }
}
