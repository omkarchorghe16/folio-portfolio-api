package com.foliopro.api.service;

import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioAssetSummary;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioAssetTypeDto;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioHistoryPoint;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSubAssetSummary;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSubAssetTypeDto;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSummaryResponse;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioTotals;
import com.foliopro.api.model.PortfolioEntryEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Derives totals, allocation and history of one user and country from its monthly entries. */
final class PortfolioSummaryCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENT_SCALE = 2;

    private PortfolioSummaryCalculator() {
    }

    /** assetTypes and subAssetTypesById name the assets; assetTypes also sets their order. */
    static PortfolioSummaryResponse summarize(Long userId, String country, String currency,
                                              List<PortfolioAssetTypeDto> assetTypes,
                                              Map<Long, PortfolioSubAssetTypeDto> subAssetTypesById,
                                              List<? extends PortfolioEntryEntity> entries) {
        TreeMap<YearMonth, List<PortfolioEntryEntity>> entriesByMonth = entries.stream()
                .collect(Collectors.groupingBy(entry -> YearMonth.from(entry.getEntryMonth()), TreeMap::new,
                        Collectors.toList()));
        if (entriesByMonth.isEmpty()) {
            return new PortfolioSummaryResponse(userId, country, currency, null, null, List.of(), List.of());
        }

        List<PortfolioHistoryPoint> history = new ArrayList<>();
        for (Map.Entry<YearMonth, List<PortfolioEntryEntity>> month : entriesByMonth.entrySet()) {
            history.add(toHistoryPoint(month.getKey(), month.getValue()));
        }
        PortfolioHistoryPoint latest = history.getLast();
        PortfolioTotals totals = new PortfolioTotals(latest.investedAmount(), latest.currentValue(), latest.profit(),
                percentOf(latest.profit(), latest.investedAmount()), monthlyReturnPercent(history));
        Map<String, List<PortfolioEntryEntity>> latestByAssetType = entriesByMonth.lastEntry().getValue().stream()
                .collect(Collectors.groupingBy(PortfolioEntryEntity::getAssetType));
        List<PortfolioAssetSummary> assets = assetTypes.stream()
                .filter(assetType -> latestByAssetType.containsKey(assetType.assetType()))
                .map(assetType -> toAssetSummary(assetType, latestByAssetType.get(assetType.assetType()),
                        subAssetTypesById, latest.currentValue()))
                .toList();
        return new PortfolioSummaryResponse(userId, country, currency, latest.month(), totals, assets, history);
    }

    /** Gain since the previous calendar month as a percentage, not counting money added or taken out. */
    private static BigDecimal monthlyReturnPercent(List<PortfolioHistoryPoint> history) {
        if (history.size() < 2) {
            return null;
        }
        PortfolioHistoryPoint latest = history.getLast();
        PortfolioHistoryPoint previous = history.get(history.size() - 2);
        if (!previous.month().equals(latest.month().minusMonths(1))) {
            return null;
        }
        // (value - previous value) - (invested - previous invested) is the change in profit.
        return percentOf(latest.profit().subtract(previous.profit()), previous.currentValue());
    }

    private static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(whole, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static PortfolioHistoryPoint toHistoryPoint(YearMonth month, List<PortfolioEntryEntity> entries) {
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        for (PortfolioEntryEntity entry : entries) {
            invested = invested.add(entry.getInvestedAmount());
            value = value.add(entry.getCurrentValue());
        }
        return new PortfolioHistoryPoint(month, invested, value, value.subtract(invested));
    }

    private static PortfolioAssetSummary toAssetSummary(PortfolioAssetTypeDto assetType,
                                                        List<PortfolioEntryEntity> entries,
                                                        Map<Long, PortfolioSubAssetTypeDto> subAssetTypesById,
                                                        BigDecimal totalValue) {
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        for (PortfolioEntryEntity entry : entries) {
            invested = invested.add(entry.getInvestedAmount());
            value = value.add(entry.getCurrentValue());
        }
        BigDecimal profit = value.subtract(invested);
        List<PortfolioSubAssetSummary> subAssets = entries.stream()
                .filter(entry -> entry.getSubAssetTypeId() != null)
                .sorted(Comparator.comparing(PortfolioEntryEntity::getSubAssetTypeId))
                .map(entry -> toSubAssetSummary(subAssetTypesById.get(entry.getSubAssetTypeId()), entry))
                .toList();
        return new PortfolioAssetSummary(assetType.assetType(), assetType.name(), invested, value, profit,
                percentOf(profit, invested), percentOf(value, totalValue), subAssets);
    }

    private static PortfolioSubAssetSummary toSubAssetSummary(PortfolioSubAssetTypeDto subAssetType,
                                                              PortfolioEntryEntity entry) {
        BigDecimal profit = entry.getCurrentValue().subtract(entry.getInvestedAmount());
        return new PortfolioSubAssetSummary(subAssetType.subAssetType(), subAssetType.name(),
                entry.getInvestedAmount(), entry.getCurrentValue(), profit,
                percentOf(profit, entry.getInvestedAmount()));
    }
}
