package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.IssuedProofApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proofs")
public class ProofController {

    private final IssuedProofApplicationService issuedProofApplicationService;

    public ProofController(IssuedProofApplicationService issuedProofApplicationService) {
        this.issuedProofApplicationService = issuedProofApplicationService;
    }

    @PostMapping("/issue")
    public Object issue(@Valid @RequestBody IssueProofRequest request) {
        return issuedProofApplicationService.issue(new IssuedProofApplicationService.IssueCommand(
                request.userId(),
                request.deviceId(),
                request.assetCode()
        ));
    }

    public record IssueProofRequest(
            @Positive long userId,
            @NotBlank String deviceId,
            String assetCode
    ) {}
}
