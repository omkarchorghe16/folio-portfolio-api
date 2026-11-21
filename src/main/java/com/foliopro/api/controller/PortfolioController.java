package com.foliopro.api.controller;

import com.foliopro.api.dto.ApiDtos.*;
import com.foliopro.api.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolios")
@Validated
@Tag(name = "Portfolios", description = "Portfolio and holdings endpoints")
public class PortfolioController {
    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Operation(summary = "List portfolios",
            description = "Returns portfolios using zero-based pagination; page size is limited to 100.")
    @GetMapping
    public PageResponse<PortfolioResponse> listPortfolios(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "15") @Min(1) @Max(100) int size) {
        return portfolioService.listPortfolios(page, size);
    }

    @Operation(summary = "Create a portfolio", description = "Creates a portfolio and its optional holdings.")
    @PostMapping
    public PortfolioResponse createPortfolio(@Valid @RequestBody PortfolioRequest request) {
        return portfolioService.createPortfolio(request);
    }

    @Operation(summary = "Get a portfolio", description = "Returns one portfolio and its holdings.")
    @GetMapping("/{id}")
    public PortfolioResponse getPortfolio(@PathVariable @Positive long id) {
        return portfolioService.getPortfolio(id);
    }

    @Operation(summary = "Replace a portfolio", description = "Replaces the portfolio fields and holdings.")
    @PutMapping("/{id}")
    public PortfolioResponse replacePortfolio(@PathVariable @Positive long id,
                                              @Valid @RequestBody PortfolioRequest request) {
        return portfolioService.replacePortfolio(id, request);
    }

    @Operation(summary = "Delete a portfolio")
    @DeleteMapping("/{id}")
    public void deletePortfolio(@PathVariable @Positive long id) {
        portfolioService.deletePortfolio(id);
    }
}
