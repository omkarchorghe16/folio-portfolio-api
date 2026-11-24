package com.foliopro.api.controller;

import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioMonthRequest;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioMonthResponse;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSummaryResponse;
import com.foliopro.api.service.PortfolioEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/users/{userId}/portfolio-entries/{market}")
@Validated
@Tag(name = "Portfolio entries",
        description = "Monthly invested amount and current value per asset type (market: US, USA or India)")
public class PortfolioEntryController {
    private final PortfolioEntryService portfolioEntryService;

    public PortfolioEntryController(PortfolioEntryService portfolioEntryService) {
        this.portfolioEntryService = portfolioEntryService;
    }

    @Operation(summary = "Get the portfolio summary",
            description = "Totals, per-asset lines and allocation of the latest recorded month, plus the totals of "
                    + "every recorded month, oldest first. Empty lists when nothing is recorded.")
    @GetMapping("/summary")
    public PortfolioSummaryResponse getSummary(@PathVariable @Positive Long userId, @PathVariable String market) {
        return portfolioEntryService.getSummary(userId, market);
    }

    @Operation(summary = "Get one month's entries",
            description = "month is yyyy-MM. When the month has no entries, saved is false and the entries are copied "
                    + "from the latest earlier month (sourceMonth) as a suggestion, without hidden types. "
                    + "assetTypes and subAssetTypes list every one.")
    @GetMapping("/months/{month}")
    public PortfolioMonthResponse getMonth(@PathVariable @Positive Long userId, @PathVariable String market,
                                           @PathVariable YearMonth month) {
        return portfolioEntryService.getMonth(userId, market, month);
    }

    @Operation(summary = "Replace one month's entries",
            description = "month is yyyy-MM and not in the future. The request is the month's complete content: "
                    + "asset types left out are deleted. investedAmount is the total cost still held. "
                    + "assetTypes (optional) adds the user's own types (code CUSTOM_ and 32 hex digits) and renames "
                    + "types for every month. "
                    + "subAssetTypes does the same for sub asset types (code SUB_ and 32 hex digits). "
                    + "In one month an asset type has either its own entry or entries of its sub asset types.")
    @PutMapping("/months/{month}")
    public PortfolioMonthResponse replaceMonth(@PathVariable @Positive Long userId, @PathVariable String market,
                                               @PathVariable YearMonth month,
                                               @Valid @RequestBody PortfolioMonthRequest request) {
        return portfolioEntryService.replaceMonth(userId, market, month, request);
    }
}
