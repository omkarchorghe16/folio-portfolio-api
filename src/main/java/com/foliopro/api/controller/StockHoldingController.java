package com.foliopro.api.controller;

import com.foliopro.api.dto.StockHoldingDtos.StockHoldingPositionRequest;
import com.foliopro.api.dto.StockHoldingDtos.StockHoldingRequest;
import com.foliopro.api.dto.StockHoldingDtos.StockHoldingResponse;
import com.foliopro.api.service.StockHoldingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/stock-holdings/{market}")
@Validated
@Tag(name = "Stock holdings",
        description = "Stocks and ETFs a user holds, without prices (market: US, USA or India)")
public class StockHoldingController {
    private final StockHoldingService stockHoldingService;

    public StockHoldingController(StockHoldingService stockHoldingService) {
        this.stockHoldingService = stockHoldingService;
    }

    @Operation(summary = "List stock holdings", description = "All of the user's holdings in the market, by symbol.")
    @GetMapping
    public List<StockHoldingResponse> listHoldings(@PathVariable @Positive Long userId, @PathVariable String market) {
        return stockHoldingService.listHoldings(userId, market);
    }

    @Operation(summary = "Add a stock holding",
            description = "India symbols are NSE symbols; a .NS or .BO suffix is removed. 409 if already held.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockHoldingResponse createHolding(@PathVariable @Positive Long userId, @PathVariable String market,
                                              @Valid @RequestBody StockHoldingRequest request) {
        return stockHoldingService.createHolding(userId, market, request);
    }

    @Operation(summary = "Change a holding's quantity and average cost")
    @PutMapping("/{id}")
    public StockHoldingResponse updateHoldingPosition(@PathVariable @Positive Long userId,
                                                      @PathVariable String market, @PathVariable @Positive Long id,
                                                      @Valid @RequestBody StockHoldingPositionRequest request) {
        return stockHoldingService.updateHoldingPosition(userId, market, id, request);
    }

    @Operation(summary = "Remove a stock holding")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHolding(@PathVariable @Positive Long userId, @PathVariable String market,
                              @PathVariable @Positive Long id) {
        stockHoldingService.deleteHolding(userId, market, id);
    }
}
