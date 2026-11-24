package com.foliopro.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public final class PortfolioEntryDtos {
    private PortfolioEntryDtos() {}

    /**
     * assetType is the code that entries refer to; it never changes, the name may. Hidden types are not suggested for
     * new months but keep the amounts of past months. A request without hidden leaves it as it is.
     */
    public record PortfolioAssetTypeDto(
            @NotBlank @Size(max = 40) String assetType,
            @NotBlank @Size(max = 60) String name,
            Boolean hidden) {}

    /** subAssetType is a fixed code; assetType is the code of the asset type it belongs to. hidden is as above. */
    public record PortfolioSubAssetTypeDto(
            @NotBlank @Size(max = 40) String subAssetType,
            @NotBlank @Size(max = 40) String assetType,
            @NotBlank @Size(max = 60) String name,
            Boolean hidden) {}

    /**
     * Without subAssetType the amounts are for the asset type itself. In one month an asset type has either its own
     * entry or entries of its sub asset types, not both. investedAmount is the total cost still held in that
     * month, not the amount added in it.
     */
    public record PortfolioEntryDto(
            @NotBlank @Size(max = 40) String assetType,
            @Size(max = 40) String subAssetType,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal investedAmount,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal currentValue) {}

    /**
     * entries is the month's complete list; an empty list clears the month. assetTypes and subAssetTypes (both
     * optional) list only new and changed ones; they apply to every month.
     */
    public record PortfolioMonthRequest(
            @NotNull @Size(max = 500) List<@Valid @NotNull PortfolioEntryDto> entries,
            @Size(max = 100) List<@Valid @NotNull PortfolioAssetTypeDto> assetTypes,
            @Size(max = 500) List<@Valid @NotNull PortfolioSubAssetTypeDto> subAssetTypes) {}

    /**
     * When saved is false the month has no rows: entries are a suggestion copied from sourceMonth (the latest
     * earlier month), or empty with no sourceMonth when there is none. assetTypes and subAssetTypes list
     * every one in display order.
     */
    public record PortfolioMonthResponse(Long userId, String country, String currency, YearMonth month,
            boolean saved, YearMonth sourceMonth, List<PortfolioEntryDto> entries,
            List<PortfolioAssetTypeDto> assetTypes, List<PortfolioSubAssetTypeDto> subAssetTypes) {}

    /** Percentages are absent when their denominator is zero or the previous calendar month is not recorded. */
    public record PortfolioTotals(BigDecimal investedAmount, BigDecimal currentValue, BigDecimal profit,
            BigDecimal profitPercent, BigDecimal monthlyReturnPercent) {}

    public record PortfolioSubAssetSummary(String subAssetType, String name, BigDecimal investedAmount,
            BigDecimal currentValue, BigDecimal profit, BigDecimal profitPercent) {}

    /** Amounts are the sum of subAssets, or the asset type's own amounts when subAssets is empty. */
    public record PortfolioAssetSummary(String assetType, String name, BigDecimal investedAmount,
            BigDecimal currentValue, BigDecimal profit, BigDecimal profitPercent, BigDecimal allocationPercent,
            List<PortfolioSubAssetSummary> subAssets) {}

    public record PortfolioHistoryPoint(YearMonth month, BigDecimal investedAmount, BigDecimal currentValue,
            BigDecimal profit) {}

    /** latestMonth and totals are absent when nothing is recorded; history is oldest first. */
    public record PortfolioSummaryResponse(Long userId, String country, String currency, YearMonth latestMonth,
            PortfolioTotals totals, List<PortfolioAssetSummary> assets, List<PortfolioHistoryPoint> history) {}
}
