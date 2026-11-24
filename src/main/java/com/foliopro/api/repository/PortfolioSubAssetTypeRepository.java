package com.foliopro.api.repository;

import com.foliopro.api.model.PortfolioSubAssetTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/** Queries shared by the USA and India sub asset type tables; each table has its own repository. */
@NoRepositoryBean
public interface PortfolioSubAssetTypeRepository<T extends PortfolioSubAssetTypeEntity>
        extends JpaRepository<T, Long> {
    /** Oldest first, which is the display order within an asset type. */
    List<T> findByUserIdOrderByIdAsc(Long userId);
}
