package com.foliopro.api.controller;

import com.foliopro.api.dto.ApiDtos.*;
import com.foliopro.api.service.FolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") @Validated
@Tag(name = "Portfolios", description = "Portfolio and holdings endpoints")
public class ApiController {
 private final FolioService service;
 public ApiController(FolioService s){service=s;}
 @Operation(summary="List portfolios",description="Returns portfolios using zero-based pagination; page size is limited to 100.")
 @GetMapping("/portfolios") public PageResponse<PortfolioResponse> portfolios(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="15") @Min(1) @Max(100) int size){return service.listPortfolios(page,size);}
 @Operation(summary="Create a portfolio",description="Creates a portfolio and its optional holdings.")
 @PostMapping("/portfolios") public PortfolioResponse createPortfolio(@Valid @RequestBody PortfolioRequest r){return service.savePortfolio(null,r);}
 @Operation(summary="Get a portfolio",description="Returns one portfolio and its holdings.")
 @GetMapping("/portfolios/{id}") public PortfolioResponse portfolio(@PathVariable @Positive long id){return service.getPortfolio(id);}
 @Operation(summary="Replace a portfolio",description="Replaces the portfolio fields and holdings.")
 @PutMapping("/portfolios/{id}") public PortfolioResponse updatePortfolio(@PathVariable @Positive long id,@Valid @RequestBody PortfolioRequest r){return service.savePortfolio(id,r);}
 @Operation(summary="Delete a portfolio")
 @DeleteMapping("/portfolios/{id}") public void deletePortfolio(@PathVariable @Positive long id){service.deletePortfolio(id);}
}
