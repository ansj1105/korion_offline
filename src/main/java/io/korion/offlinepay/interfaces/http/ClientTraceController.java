package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.ClientTraceReportService;
import io.korion.offlinepay.interfaces.http.dto.RecordClientTraceRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offline-pay/client-traces")
public class ClientTraceController {

    private final ClientTraceReportService clientTraceReportService;

    public ClientTraceController(ClientTraceReportService clientTraceReportService) {
        this.clientTraceReportService = clientTraceReportService;
    }

    @PostMapping
    public ResponseEntity<ClientTraceReportService.ClientTraceReportResult> record(
            @Valid @RequestBody RecordClientTraceRequest request
    ) {
        return ResponseEntity.accepted().body(clientTraceReportService.record(request));
    }
}
