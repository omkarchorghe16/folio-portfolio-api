package com.foliopro.api.service;

import com.foliopro.api.dto.PortfolioEntryDtos.PortfolioSubAssetTypeDto;
import com.foliopro.api.model.IndiaPortfolioSubAssetTypeEntity;
import com.foliopro.api.model.PortfolioSubAssetTypeEntity;
import com.foliopro.api.model.UsaPortfolioSubAssetTypeEntity;
import com.foliopro.api.model.WatchlistMarket;
import com.foliopro.api.repository.IndiaPortfolioSubAssetTypeRepository;
import com.foliopro.api.repository.PortfolioSubAssetTypeRepository;
import com.foliopro.api.repository.UsaPortfolioSubAssetTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** A user's sub asset types in one country; each belongs to one asset type. */
@Service
public class PortfolioSubAssetTypeService {
    /** Clients create the code, so that repeating a request does not add the sub asset type twice. */
    private static final Pattern SUB_CODE = Pattern.compile("SUB_[0-9A-F]{32}");

    private final UsaPortfolioSubAssetTypeRepository usaPortfolioSubAssetTypeRepository;
    private final IndiaPortfolioSubAssetTypeRepository indiaPortfolioSubAssetTypeRepository;
    private final PortfolioAssetTypeService portfolioAssetTypeService;

    public PortfolioSubAssetTypeService(UsaPortfolioSubAssetTypeRepository usaPortfolioSubAssetTypeRepository,
                                        IndiaPortfolioSubAssetTypeRepository indiaPortfolioSubAssetTypeRepository,
                                        PortfolioAssetTypeService portfolioAssetTypeService) {
        this.usaPortfolioSubAssetTypeRepository = usaPortfolioSubAssetTypeRepository;
        this.indiaPortfolioSubAssetTypeRepository = indiaPortfolioSubAssetTypeRepository;
        this.portfolioAssetTypeService = portfolioAssetTypeService;
    }

    /** Keyed by database id, oldest first. */
    @Transactional(readOnly = true)
    public Map<Long, PortfolioSubAssetTypeDto> findSubAssetTypesById(Long userId, String market) {
        Map<Long, String> assetTypeCodesById = portfolioAssetTypeService.findCodesById(userId, market);
        Map<Long, PortfolioSubAssetTypeDto> subAssetTypesById = new LinkedHashMap<>();
        repositoryOf(market).findByUserIdOrderByIdAsc(userId).forEach(saved -> subAssetTypesById.put(saved.getId(),
                new PortfolioSubAssetTypeDto(saved.getCode(), assetTypeCodesById.get(saved.getAssetTypeId()),
                        saved.getName(), saved.isHidden())));
        return subAssetTypesById;
    }

    /** Adds new sub asset types and renames, hides or shows existing ones; those left out stay as they are. */
    @Transactional
    public void saveSubAssetTypes(Long userId, String market, List<PortfolioSubAssetTypeDto> changes) {
        if (changes.isEmpty()) {
            return;
        }
        if (WatchlistMarket.isUs(market)) {
            saveChanges(usaPortfolioSubAssetTypeRepository, UsaPortfolioSubAssetTypeEntity::new, userId, market,
                    changes);
        } else {
            saveChanges(indiaPortfolioSubAssetTypeRepository, IndiaPortfolioSubAssetTypeEntity::new, userId, market,
                    changes);
        }
    }

    private <T extends PortfolioSubAssetTypeEntity> void saveChanges(
            PortfolioSubAssetTypeRepository<T> repository, Supplier<T> newSubAssetType, Long userId, String market,
            List<PortfolioSubAssetTypeDto> changes) {
        Map<String, Long> assetTypeIdsByCode = portfolioAssetTypeService.ensureAssetTypes(userId, market,
                changes.stream().map(PortfolioSubAssetTypeDto::assetType).toList());
        Map<String, T> savedByCode = new HashMap<>();
        repository.findByUserIdOrderByIdAsc(userId).forEach(saved -> savedByCode.put(saved.getCode(), saved));
        for (PortfolioSubAssetTypeDto change : changes) {
            Long assetTypeId = assetTypeIdsByCode.get(change.assetType());
            T subAssetType = savedByCode.get(change.subAssetType());
            if (subAssetType == null) {
                if (!SUB_CODE.matcher(change.subAssetType()).matches()) {
                    throw new IllegalArgumentException("Unknown sub asset type: " + change.subAssetType());
                }
                subAssetType = newSubAssetType.get();
                subAssetType.setUserId(userId);
                subAssetType.setAssetTypeId(assetTypeId);
                subAssetType.setCode(change.subAssetType());
                savedByCode.put(change.subAssetType(), subAssetType);
            } else if (!subAssetType.getAssetTypeId().equals(assetTypeId)) {
                throw new IllegalArgumentException("A sub asset type cannot move to another asset type");
            }
            subAssetType.setName(change.name().trim());
            if (change.hidden() != null) {
                subAssetType.setHidden(change.hidden());
            }
            repository.save(subAssetType);
        }
        repository.flush();
        requireDistinctNames(savedByCode);
    }

    /** The same name may be used under different asset types. */
    private static void requireDistinctNames(Map<String, ? extends PortfolioSubAssetTypeEntity> savedByCode) {
        Set<String> names = new HashSet<>();
        for (PortfolioSubAssetTypeEntity subAssetType : savedByCode.values()) {
            if (!names.add(subAssetType.getAssetTypeId() + "/" + subAssetType.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Sub asset type " + subAssetType.getName() + " already exists");
            }
        }
    }

    private PortfolioSubAssetTypeRepository<? extends PortfolioSubAssetTypeEntity> repositoryOf(String market) {
        return WatchlistMarket.isUs(market) ? usaPortfolioSubAssetTypeRepository : indiaPortfolioSubAssetTypeRepository;
    }
}
