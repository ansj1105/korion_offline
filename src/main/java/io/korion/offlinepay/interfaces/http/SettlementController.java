package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.SettlementApplicationService;
import io.korion.offlinepay.interfaces.http.factory.SettlementResponseFactory;
import io.korion.offlinepay.interfaces.http.dto.ConfirmReceivedSettlementsRequest;
import io.korion.offlinepay.interfaces.http.dto.SubmitSettlementBatchRequest;
import io.korion.offlinepay.interfaces.http.dto.SubmitLocalEvidenceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementApplicationService settlementApplicationService;
    private final SettlementResponseFactory settlementResponseFactory;

    public SettlementController(
            SettlementApplicationService settlementApplicationService,
            SettlementResponseFactory settlementResponseFactory
    ) {
        this.settlementApplicationService = settlementApplicationService;
        this.settlementResponseFactory = settlementResponseFactory;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Object submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitSettlementBatchRequest request
    ) {
        var batch = settlementApplicationService.submitBatch(request.toCommand(idempotencyKey));
        return settlementResponseFactory.toBatchDetail(batch);
    }

    @PostMapping("/local-evidence")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Object submitLocalEvidence(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitLocalEvidenceRequest request
    ) {
        return settlementApplicationService.ingestLocalEvidence(request.toCommand(idempotencyKey));
    }

    @GetMapping("/{batchId}")
    public Object getBatch(@PathVariable String batchId) {
        var batch = settlementApplicationService.getBatchDetail(batchId);
        return settlementResponseFactory.toBatchDetail(batch);
    }

    @GetMapping("/requests/{settlementId}")
    public Object getSettlement(@PathVariable String settlementId) {
        var settlement = settlementApplicationService.getSettlementDetail(settlementId);
        return settlementResponseFactory.toDetailResponse(settlement);
    }

    @PostMapping("/{settlementId}/finalize")
    public Object finalizeSettlement(@PathVariable String settlementId) {
        var settlement = settlementApplicationService.finalizeSettlement(settlementId);
        return settlementResponseFactory.toFinalizeResponse(settlement);
    }

    @PostMapping("/received/confirm")
    public Object confirmReceivedSettlements(@Valid @RequestBody ConfirmReceivedSettlementsRequest request) {
        return settlementApplicationService.confirmReceivedSettlements(request.toCommand());
    }
}
