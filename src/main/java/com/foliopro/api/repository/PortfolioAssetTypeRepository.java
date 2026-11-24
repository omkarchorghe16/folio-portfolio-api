package com.foliopro.api.repository;

import com.foliopro.api.model.PortfolioAssetTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/** Queries shared by the USA and India asset type tables; each table has its own repository. */
@NoRepositoryBean
public interface PortfolioAssetTypeRepository<T extends PortfolioAssetTypeEntity> extends JpaRepository<T, Long> {
    /** Oldest first, which is the display order of a user's own types. */
    List<T> findByUserIdOrderByIdAsc(Long userId);
}
