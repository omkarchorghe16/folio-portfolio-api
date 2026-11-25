package com.foliopro.api.dto;

import com.foliopro.api.model.StockHoldingAssetType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class StockHoldingDtos {
    private StockHoldingDtos() {}

    /** averageCost is per share; quantity (null when not known) and sector are optional. The symbol's format depends on the market and is checked by the service. */
    public record StockHoldingRequest(
            @NotBlank @Size(max = 24) String symbol,
            @NotBlank @Size(max = 160) String name,
            @NotNull StockHoldingAssetType assetType,
            @DecimalMin("0.00000001") @Digits(integer = 12, fraction = 8) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal averageCost,
            @Size(max = 100) String sector) {}

    /** Replaces all of these values; without a quantity or sector the holding has none. */
    public record StockHoldingPositionRequest(
            @DecimalMin("0.00000001") @Digits(integer = 12, fraction = 8) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal averageCost,
            @Size(max = 100) String sector) {}

    /** exchange is present for India only. */
    public record StockHoldingResponse(Long id, Long userId, String country, String currency, String symbol,
            String exchange, String name, StockHoldingAssetType assetType, BigDecimal quantity,
            BigDecimal averageCost, String sector) {}
}
