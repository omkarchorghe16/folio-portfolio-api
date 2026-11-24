package com.foliopro.api.service;

import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioAssetTypeDto;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioEntryDto;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioMonthRequest;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioMonthResponse;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSubAssetTypeDto;
import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSummaryResponse;
import com.foliopro.api.model.IndiaPortfolioEntryEntity;
import com.foliopro.api.model.PortfolioEntryEntity;
import com.foliopro.api.model.UsaPortfolioEntryEntity;
import com.foliopro.api.model.WatchlistMarket;
import com.foliopro.api.repository.IndiaPortfolioEntryRepository;
import com.foliopro.api.repository.PortfolioEntryRepository;
import com.foliopro.api.repository.UsaPortfolioEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Monthly invested amounts and values per asset type, kept in separate USA and India tables. */
@Service
public class PortfolioEntryService {
    private static final String USD = "USD";
    private static final String INR = "INR";

    private final UsaPortfolioEntryRepository usaPortfolioEntryRepository;
    private final IndiaPortfolioEntryRepository indiaPortfolioEntryRepository;
    private final PortfolioAssetTypeService portfolioAssetTypeService;
    private final PortfolioSubAssetTypeService portfolioSubAssetTypeService;

    public PortfolioEntryService(UsaPortfolioEntryRepository usaPortfolioEntryRepository,
                                 IndiaPortfolioEntryRepository indiaPortfolioEntryRepository,
                                 PortfolioAssetTypeService portfolioAssetTypeService,
                                 PortfolioSubAssetTypeService portfolioSubAssetTypeService) {
        this.usaPortfolioEntryRepository = usaPortfolioEntryRepository;
        this.indiaPortfolioEntryRepository = indiaPortfolioEntryRepository;
        this.portfolioAssetTypeService = portfolioAssetTypeService;
        this.portfolioSubAssetTypeService = portfolioSubAssetTypeService;
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long userId, String market) {
        String country = WatchlistMarket.normalize(market);
        return PortfolioSummaryCalculator.summarize(userId, country, currencyOf(country),
                portfolioAssetTypeService.listAssetTypes(userId, country),
                portfolioSubAssetTypeService.findSubAssetTypesById(userId, country),
                repositoryOf(country).findByUserIdOrderByEntryMonthAsc(userId));
    }

    /**
     * Returns the month's entries or, when it has none, the latest earlier month's entries as a suggestion. Hidden
     * asset types and sub asset types are left out of a suggestion.
     */
    @Transactional(readOnly = true)
    public PortfolioMonthResponse getMonth(Long userId, String market, YearMonth month) {
        String country = WatchlistMarket.normalize(market);
        AssetTypes assetTypes = findAssetTypes(userId, country);
        PortfolioEntryRepository<? extends PortfolioEntryEntity> repository = repositoryOf(country);
        List<? extends PortfolioEntryEntity> entries = repository.findByUserIdAndEntryMonth(userId, month.atDay(1));
        if (!entries.isEmpty()) {
            return toResponse(userId, country, month, true, null, entries, assetTypes);
        }
        Optional<LocalDate> sourceMonth = repository
                .findFirstByUserIdAndEntryMonthBeforeOrderByEntryMonthDesc(userId, month.atDay(1))
                .map(PortfolioEntryEntity::getEntryMonth);
        if (sourceMonth.isEmpty()) {
            return toResponse(userId, country, month, false, null, List.of(), assetTypes);
        }
        List<? extends PortfolioEntryEntity> suggested =
                repository.findByUserIdAndEntryMonth(userId, sourceMonth.get()).stream()
                        .filter(entry -> !assetTypes.isHidden(entry))
                        .toList();
        return toResponse(userId, country, month, false, YearMonth.from(sourceMonth.get()), suggested, assetTypes);
    }

    /**
     * Saves new and changed asset types and sub asset types, then makes the request the month's complete content:
     * updates, inserts and deletes rows. All of it happens in one transaction.
     */
    @Transactional
    public PortfolioMonthResponse replaceMonth(Long userId, String market, YearMonth month,
                                               PortfolioMonthRequest request) {
        String country = WatchlistMarket.normalize(market);
        if (month.isAfter(YearMonth.now())) {
            throw new IllegalArgumentException("Month must not be in the future");
        }
        if (request.assetTypes() != null) {
            portfolioAssetTypeService.saveAssetTypes(userId, country, request.assetTypes());
        }
        if (request.subAssetTypes() != null) {
            portfolioSubAssetTypeService.saveSubAssetTypes(userId, country, request.subAssetTypes());
        }
        AssetTypes assetTypes = findAssetTypes(userId, country);
        Map<EntryKey, PortfolioEntryDto> requestedByKey = indexByKey(request.entries(), assetTypes);
        List<? extends PortfolioEntryEntity> saved = WatchlistMarket.isUs(country)
                ? saveMonth(usaPortfolioEntryRepository, UsaPortfolioEntryEntity::new, userId, month,
                        requestedByKey)
                : saveMonth(indiaPortfolioEntryRepository, IndiaPortfolioEntryEntity::new, userId, month,
                        requestedByKey);
        return toResponse(userId, country, month, !saved.isEmpty(), null, saved, assetTypes);
    }

    private <T extends PortfolioEntryEntity> List<T> saveMonth(
            PortfolioEntryRepository<T> repository, Supplier<T> newEntry, Long userId, YearMonth month,
            Map<EntryKey, PortfolioEntryDto> requestedByKey) {
        Map<EntryKey, T> existingByKey = new HashMap<>();
        repository.findByUserIdAndEntryMonth(userId, month.atDay(1)).forEach(entry ->
                existingByKey.put(new EntryKey(entry.getAssetType(), entry.getSubAssetTypeId()), entry));

        List<T> entriesToSave = new ArrayList<>();
        for (Map.Entry<EntryKey, PortfolioEntryDto> requested : requestedByKey.entrySet()) {
            T entry = existingByKey.remove(requested.getKey());
            if (entry == null) {
                entry = newEntry.get();
                entry.setUserId(userId);
                entry.setEntryMonth(month.atDay(1));
                entry.setAssetType(requested.getKey().assetType());
                entry.setSubAssetTypeId(requested.getKey().subAssetTypeId());
            }
            entry.setAmounts(requested.getValue().investedAmount(), requested.getValue().currentValue());
            entriesToSave.add(entry);
        }
        repository.deleteAll(existingByKey.values());
        return repository.saveAllAndFlush(entriesToSave);
    }

    private static Map<EntryKey, PortfolioEntryDto> indexByKey(List<PortfolioEntryDto> entries,
                                                               AssetTypes assetTypes) {
        List<String> knownCodes = assetTypes.orderedCodes();
        Map<EntryKey, PortfolioEntryDto> entriesByKey = new LinkedHashMap<>();
        for (PortfolioEntryDto entry : entries) {
            if (!knownCodes.contains(entry.assetType())) {
                throw new IllegalArgumentException("Unknown asset type: " + entry.assetType());
            }
            EntryKey key = new EntryKey(entry.assetType(), assetTypes.findSubAssetTypeId(entry));
            if (entriesByKey.put(key, entry) != null) {
                throw new IllegalArgumentException("Duplicate asset type: " + entry.assetType());
            }
        }
        for (EntryKey key : entriesByKey.keySet()) {
            if (key.subAssetTypeId() != null && entriesByKey.containsKey(new EntryKey(key.assetType(), null))) {
                throw new IllegalArgumentException("Asset type " + key.assetType()
                        + " has amounts of its own and of its sub asset types");
            }
        }
        return entriesByKey;
    }

    private AssetTypes findAssetTypes(Long userId, String country) {
        return new AssetTypes(portfolioAssetTypeService.listAssetTypes(userId, country),
                portfolioSubAssetTypeService.findSubAssetTypesById(userId, country));
    }

    private PortfolioEntryRepository<? extends PortfolioEntryEntity> repositoryOf(String country) {
        return WatchlistMarket.isUs(country) ? usaPortfolioEntryRepository : indiaPortfolioEntryRepository;
    }

    private static String currencyOf(String country) {
        return WatchlistMarket.isUs(country) ? USD : INR;
    }

    private static PortfolioMonthResponse toResponse(Long userId, String country, YearMonth month, boolean saved,
                                                     YearMonth sourceMonth,
                                                     List<? extends PortfolioEntryEntity> entries,
                                                     AssetTypes assetTypes) {
        List<String> orderedCodes = assetTypes.orderedCodes();
        Map<Long, PortfolioSubAssetTypeDto> subAssetTypesById = assetTypes.subAssetTypesById();
        List<PortfolioEntryDto> entryDtos = entries.stream()
                .sorted(Comparator
                        .comparingInt((PortfolioEntryEntity entry) -> orderedCodes.indexOf(entry.getAssetType()))
                        .thenComparing(PortfolioEntryEntity::getSubAssetTypeId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(entry -> new PortfolioEntryDto(entry.getAssetType(),
                        entry.getSubAssetTypeId() == null
                                ? null : subAssetTypesById.get(entry.getSubAssetTypeId()).subAssetType(),
                        entry.getInvestedAmount(), entry.getCurrentValue()))
                .toList();
        return new PortfolioMonthResponse(userId, country, currencyOf(country), month, saved, sourceMonth, entryDtos,
                assetTypes.assetTypes(), List.copyOf(subAssetTypesById.values()));
    }

    /** What an entry is for within one user's month; subAssetTypeId is null for the asset type itself. */
    private record EntryKey(String assetType, Long subAssetTypeId) {
    }

    /** One user's asset types in display order and their sub asset types by database id. */
    private record AssetTypes(List<PortfolioAssetTypeDto> assetTypes,
                              Map<Long, PortfolioSubAssetTypeDto> subAssetTypesById) {
        private List<String> orderedCodes() {
            return assetTypes.stream().map(PortfolioAssetTypeDto::assetType).toList();
        }

        /** Whether the entry's asset type or sub asset type is hidden. */
        private boolean isHidden(PortfolioEntryEntity entry) {
            boolean assetTypeHidden = assetTypes.stream()
                    .anyMatch(assetType -> assetType.assetType().equals(entry.getAssetType()) && assetType.hidden());
            return assetTypeHidden || (entry.getSubAssetTypeId() != null
                    && subAssetTypesById.get(entry.getSubAssetTypeId()).hidden());
        }

        /** Null when the entry is for the asset type itself. */
        private Long findSubAssetTypeId(PortfolioEntryDto entry) {
            if (entry.subAssetType() == null) {
                return null;
            }
            return subAssetTypesById.entrySet().stream()
                    .filter(saved -> saved.getValue().subAssetType().equals(entry.subAssetType())
                            && saved.getValue().assetType().equals(entry.assetType()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown sub asset type: " + entry.subAssetType()));
        }
    }
}
