package io.korion.offlinepay.interfaces.http;

import io.korion.offlinepay.application.service.CollateralSummaryService;
import io.korion.offlinepay.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/internal/collateral")
public class CollateralInternalController {

    private final CollateralSummaryService collateralSummaryService;
    private final AppProperties properties;

    public CollateralInternalController(
            CollateralSummaryService collateralSummaryService,
            AppProperties properties
    ) {
        this.collateralSummaryService = collateralSummaryService;
        this.properties = properties;
    }

    @GetMapping("/summary")
    public CollateralSummaryService.CollateralAggregateSummary summary(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @RequestParam long userId,
            @RequestParam(required = false) String assetCode
    ) {
        if (properties.foxCoin().apiKey() == null
                || properties.foxCoin().apiKey().isBlank()
                || !properties.foxCoin().apiKey().equals(apiKey)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized");
        }
        return collateralSummaryService.getAggregateSummary(userId, assetCode);
    }
}
