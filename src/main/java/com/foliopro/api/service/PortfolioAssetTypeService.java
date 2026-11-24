package com.foliopro.api.service;

import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioAssetTypeDto;
import com.foliopro.api.model.IndiaPortfolioAssetTypeEntity;
import com.foliopro.api.model.PortfolioAssetType;
import com.foliopro.api.model.PortfolioAssetTypeEntity;
import com.foliopro.api.model.UsaPortfolioAssetTypeEntity;
import com.foliopro.api.model.WatchlistMarket;
import com.foliopro.api.repository.IndiaPortfolioAssetTypeRepository;
import com.foliopro.api.repository.PortfolioAssetTypeRepository;
import com.foliopro.api.repository.UsaPortfolioAssetTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** A user's portfolio asset types in one country: the defaults, possibly renamed, then the user's own types. */
@Service
public class PortfolioAssetTypeService {
    /** Clients create the code of a new type, so that repeating a request does not add the type twice. */
    private static final Pattern CUSTOM_CODE = Pattern.compile("CUSTOM_[0-9A-F]{32}");

    private final UsaPortfolioAssetTypeRepository usaPortfolioAssetTypeRepository;
    private final IndiaPortfolioAssetTypeRepository indiaPortfolioAssetTypeRepository;

    public PortfolioAssetTypeService(UsaPortfolioAssetTypeRepository usaPortfolioAssetTypeRepository,
                                     IndiaPortfolioAssetTypeRepository indiaPortfolioAssetTypeRepository) {
        this.usaPortfolioAssetTypeRepository = usaPortfolioAssetTypeRepository;
        this.indiaPortfolioAssetTypeRepository = indiaPortfolioAssetTypeRepository;
    }

    /** In display order. */
    @Transactional(readOnly = true)
    public List<PortfolioAssetTypeDto> listAssetTypes(Long userId, String market) {
        String country = WatchlistMarket.normalize(market);
        return toAssetTypes(country, repositoryOf(country).findByUserIdOrderByIdAsc(userId));
    }

    /**
     * Adds new types and renames, hides or shows existing ones; types left out stay as they are. Returns the full list.
     */
    @Transactional
    public List<PortfolioAssetTypeDto> saveAssetTypes(Long userId, String market,
                                                      List<PortfolioAssetTypeDto> changes) {
        String country = WatchlistMarket.normalize(market);
        return WatchlistMarket.isUs(country)
                ? saveChanges(usaPortfolioAssetTypeRepository, UsaPortfolioAssetTypeEntity::new, userId, country,
                        changes)
                : saveChanges(indiaPortfolioAssetTypeRepository, IndiaPortfolioAssetTypeEntity::new, userId, country,
                        changes);
    }

    /** Codes of the saved asset types by database id; a default type without a row is not included. */
    @Transactional(readOnly = true)
    public Map<Long, String> findCodesById(Long userId, String market) {
        Map<Long, String> codesById = new HashMap<>();
        repositoryOf(market).findByUserIdOrderByIdAsc(userId)
                .forEach(assetType -> codesById.put(assetType.getId(), assetType.getCode()));
        return codesById;
    }

    /** Database ids by code. Default types that have no row yet get one, so that sub asset types can refer to it. */
    @Transactional
    public Map<String, Long> ensureAssetTypes(Long userId, String market, List<String> codes) {
        Set<String> savedCodes = new HashSet<>(findCodesById(userId, market).values());
        List<PortfolioAssetTypeDto> missing = listAssetTypes(userId, market).stream()
                .filter(assetType -> codes.contains(assetType.assetType())
                        && !savedCodes.contains(assetType.assetType()))
                .toList();
        if (!missing.isEmpty()) {
            saveAssetTypes(userId, market, missing);
        }
        Map<String, Long> idsByCode = new HashMap<>();
        findCodesById(userId, market).forEach((id, code) -> idsByCode.put(code, id));
        for (String code : codes) {
            if (!idsByCode.containsKey(code)) {
                throw new IllegalArgumentException("Unknown asset type: " + code);
            }
        }
        return idsByCode;
    }

    private <T extends PortfolioAssetTypeEntity> List<PortfolioAssetTypeDto> saveChanges(
            PortfolioAssetTypeRepository<T> repository, Supplier<T> newAssetType, Long userId, String country,
            List<PortfolioAssetTypeDto> changes) {
        Map<String, T> savedByCode = new HashMap<>();
        repository.findByUserIdOrderByIdAsc(userId).forEach(saved -> savedByCode.put(saved.getCode(), saved));
        for (PortfolioAssetTypeDto change : changes) {
            T assetType = savedByCode.get(change.assetType());
            if (assetType == null) {
                requireValidCode(change.assetType());
                assetType = newAssetType.get();
                assetType.setUserId(userId);
                assetType.setCode(change.assetType());
                savedByCode.put(change.assetType(), assetType);
            }
            assetType.setName(change.name().trim());
            if (change.hidden() != null) {
                assetType.setHidden(change.hidden());
            }
            repository.save(assetType);
        }
        repository.flush();
        List<PortfolioAssetTypeDto> assetTypes =
                toAssetTypes(country, repository.findByUserIdOrderByIdAsc(userId));
        requireDistinctNames(assetTypes);
        return assetTypes;
    }

    private static void requireValidCode(String code) {
        if (!isDefaultCode(code) && !CUSTOM_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Unknown asset type: " + code);
        }
    }

    private static void requireDistinctNames(List<PortfolioAssetTypeDto> assetTypes) {
        Set<String> names = new HashSet<>();
        for (PortfolioAssetTypeDto assetType : assetTypes) {
            if (!names.add(assetType.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Asset type " + assetType.name() + " already exists");
            }
        }
    }

    private static boolean isDefaultCode(String code) {
        for (PortfolioAssetType assetType : PortfolioAssetType.values()) {
            if (assetType.name().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private PortfolioAssetTypeRepository<? extends PortfolioAssetTypeEntity> repositoryOf(String country) {
        return WatchlistMarket.isUs(country) ? usaPortfolioAssetTypeRepository : indiaPortfolioAssetTypeRepository;
    }

    private static List<PortfolioAssetTypeDto> toAssetTypes(String country,
                                                            List<? extends PortfolioAssetTypeEntity> saved) {
        Map<String, PortfolioAssetTypeEntity> savedByCode = new HashMap<>();
        saved.forEach(assetType -> savedByCode.put(assetType.getCode(), assetType));
        List<PortfolioAssetTypeDto> assetTypes = new ArrayList<>();
        for (PortfolioAssetType assetType : PortfolioAssetType.values()) {
            PortfolioAssetTypeEntity changed = savedByCode.get(assetType.name());
            assetTypes.add(changed == null
                    ? new PortfolioAssetTypeDto(assetType.name(), assetType.defaultName(country), false)
                    : toAssetType(changed));
        }
        saved.stream()
                .filter(assetType -> !isDefaultCode(assetType.getCode()))
                .map(PortfolioAssetTypeService::toAssetType)
                .forEach(assetTypes::add);
        return assetTypes;
    }

    private static PortfolioAssetTypeDto toAssetType(PortfolioAssetTypeEntity saved) {
        return new PortfolioAssetTypeDto(saved.getCode(), saved.getName(), saved.isHidden());
    }
}
