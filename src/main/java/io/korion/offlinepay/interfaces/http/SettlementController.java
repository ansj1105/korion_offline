package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.SettlementApplicationService;
import io.korion.offlinepay.interfaces.http.factory.SettlementResponseFactory;
import io.korion.offlinepay.interfaces.http.dto.SubmitSettlementBatchRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public Object submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitSettlementBatchRequest request
    ) {
        return settlementApplicationService.submitBatch(request.toCommand(idempotencyKey));
    }

    @GetMapping("/{batchId}")
    public Object getBatch(@PathVariable String batchId) {
        var batch = settlementApplicationService.getBatch(batchId);
        return settlementResponseFactory.toBatchDetail(batch);
    }

    @GetMapping("/requests/{settlementId}")
    public Object getSettlement(@PathVariable String settlementId) {
        var settlement = settlementApplicationService.getSettlement(settlementId);
        return settlementResponseFactory.toDetailResponse(settlement);
    }

    @PostMapping("/{settlementId}/finalize")
    public Object finalizeSettlement(@PathVariable String settlementId) {
        var settlement = settlementApplicationService.finalizeSettlement(settlementId);
        return settlementResponseFactory.toFinalizeResponse(settlement);
    }
}
