package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.OfflineLedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class OfflineLedgerController {

    private final OfflineLedgerService offlineLedgerService;

    public OfflineLedgerController(OfflineLedgerService offlineLedgerService) {
        this.offlineLedgerService = offlineLedgerService;
    }

    @GetMapping("/history")
    public OfflineLedgerService.LedgerHistoryResponse history(
            @RequestParam long userId,
            @RequestParam(required = false) String assetCode,
            @RequestParam(required = false) Integer size
    ) {
        return offlineLedgerService.getLedgerHistory(userId, assetCode, size);
    }
}
