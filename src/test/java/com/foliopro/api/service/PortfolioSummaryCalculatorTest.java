package com.foliopro.api.service;

import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioAssetTypeDto;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSubAssetTypeDto;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSummaryResponse;
import com.foliopro.api.model.PortfolioAssetType;
import com.foliopro.api.model.PortfolioEntryEntity;
import com.foliopro.api.model.UsaPortfolioEntryEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioSummaryCalculatorTest {
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private static final List<PortfolioAssetTypeDto> ASSET_TYPES = List.of(
            new PortfolioAssetTypeDto("STOCKS", "Equities", false), new PortfolioAssetTypeDto("ETFS", "ETFs", false),
            new PortfolioAssetTypeDto("LAND", "Land", false));

    private static final Map<Long, PortfolioSubAssetTypeDto> SUB_ASSET_TYPES = Map.of(
            7L, new PortfolioSubAssetTypeDto("SUB_A", "LAND", "Farm", false),
            8L, new PortfolioSubAssetTypeDto("SUB_B", "LAND", "Plot", false));

    private static PortfolioEntryEntity entry(YearMonth month, PortfolioAssetType assetType, String invested,
                                              String value) {
        PortfolioEntryEntity entry = new UsaPortfolioEntryEntity();
        entry.setEntryMonth(month.atDay(1));
        entry.setAssetType(assetType.name());
        entry.setAmounts(new BigDecimal(invested), new BigDecimal(value));
        return entry;
    }

    private static PortfolioSummaryResponse summarize(PortfolioEntryEntity... entries) {
        return PortfolioSummaryCalculator.summarize(1L, "US", "USD", ASSET_TYPES, SUB_ASSET_TYPES, List.of(entries));
    }

    @Test
    void noEntriesGiveNoTotals() {
        PortfolioSummaryResponse summary = summarize();
        assertNull(summary.latestMonth());
        assertNull(summary.totals());
        assertTrue(summary.assets().isEmpty());
        assertTrue(summary.history().isEmpty());
    }

    @Test
    void totalsAllocationAndProfitComeFromTheLatestMonth() {
        PortfolioSummaryResponse summary = summarize(
                entry(NOVEMBER, PortfolioAssetType.ETFS, "200.00", "250.00"),
                entry(NOVEMBER, PortfolioAssetType.STOCKS, "800.00", "750.00"),
                entry(OCTOBER, PortfolioAssetType.STOCKS, "900.00", "900.00"));
        assertEquals(NOVEMBER, summary.latestMonth());
        assertEquals(new BigDecimal("1000.00"), summary.totals().investedAmount());
        assertEquals(new BigDecimal("1000.00"), summary.totals().currentValue());
        assertEquals(0, summary.totals().profit().signum());
        assertEquals("STOCKS", summary.assets().get(0).assetType());
        assertEquals("Equities", summary.assets().get(0).name());
        assertEquals(new BigDecimal("-50.00"), summary.assets().get(0).profit());
        assertEquals(new BigDecimal("-6.25"), summary.assets().get(0).profitPercent());
        assertEquals(new BigDecimal("75.00"), summary.assets().get(0).allocationPercent());
        assertEquals(new BigDecimal("25.00"), summary.assets().get(1).profitPercent());
        assertEquals(List.of(OCTOBER, NOVEMBER), summary.history().stream().map(point -> point.month()).toList());
    }

    @Test
    void monthlyReturnDoesNotCountNewMoney() {
        // 500 added and 100 gained on 1000: 10%, not 60%.
        PortfolioSummaryResponse summary = summarize(
                entry(OCTOBER, PortfolioAssetType.STOCKS, "900.00", "1000.00"),
                entry(NOVEMBER, PortfolioAssetType.STOCKS, "1400.00", "1600.00"));
        assertEquals(new BigDecimal("10.00"), summary.totals().monthlyReturnPercent());
    }

    @Test
    void monthlyReturnNeedsThePreviousCalendarMonthWithValue() {
        assertNull(summarize(entry(NOVEMBER, PortfolioAssetType.STOCKS, "1.00", "2.00"))
                .totals().monthlyReturnPercent());
        assertNull(summarize(
                entry(OCTOBER.minusMonths(1), PortfolioAssetType.STOCKS, "1.00", "2.00"),
                entry(NOVEMBER, PortfolioAssetType.STOCKS, "1.00", "3.00")).totals().monthlyReturnPercent());
        assertNull(summarize(
                entry(OCTOBER, PortfolioAssetType.STOCKS, "1.00", "0.00"),
                entry(NOVEMBER, PortfolioAssetType.STOCKS, "1.00", "3.00")).totals().monthlyReturnPercent());
    }

    @Test
    void subAssetTypesAreSummedIntoTheirAssetType() {
        PortfolioEntryEntity plot = entry(NOVEMBER, PortfolioAssetType.LAND, "100.00", "150.00");
        plot.setSubAssetTypeId(8L);
        PortfolioEntryEntity farm = entry(NOVEMBER, PortfolioAssetType.LAND, "200.00", "250.00");
        farm.setSubAssetTypeId(7L);
        PortfolioSummaryResponse summary = summarize(plot, farm,
                entry(NOVEMBER, PortfolioAssetType.STOCKS, "400.00", "400.00"));
        assertTrue(summary.assets().get(0).subAssets().isEmpty());
        assertEquals(new BigDecimal("300.00"), summary.assets().get(1).investedAmount());
        assertEquals(new BigDecimal("400.00"), summary.assets().get(1).currentValue());
        assertEquals(new BigDecimal("50.00"), summary.assets().get(1).allocationPercent());
        assertEquals(List.of("Farm", "Plot"),
                summary.assets().get(1).subAssets().stream().map(subAsset -> subAsset.name()).toList());
        assertEquals(new BigDecimal("50.00"), summary.assets().get(1).subAssets().get(1).profitPercent());
    }

    @Test
    void zeroDenominatorsGiveNoPercentages() {
        PortfolioSummaryResponse summary = summarize(entry(NOVEMBER, PortfolioAssetType.LAND, "0.00", "0.00"));
        assertNull(summary.totals().profitPercent());
        assertNull(summary.assets().get(0).profitPercent());
        assertNull(summary.assets().get(0).allocationPercent());
    }
}
