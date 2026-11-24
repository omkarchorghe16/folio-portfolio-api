package com.foliopro.api.repository;

import com.foliopro.api.model.StockHoldingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/** Queries shared by the USA and India holding tables; each table has its own repository. */
@NoRepositoryBean
public interface StockHoldingRepository<T extends StockHoldingEntity> extends JpaRepository<T, Long> {
    List<T> findByUserIdOrderBySymbolAsc(Long userId);

    Optional<T> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndSymbol(Long userId, String symbol);
}
