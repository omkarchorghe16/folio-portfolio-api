package com.foliopro.api.repository;

import com.foliopro.api.model.WatchlistEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistEntity, Long> {
    List<WatchlistEntity> findByMarket(String market, Sort sort);

    /** Oldest first, so the earliest of several same-named watchlists is found first. */
    List<WatchlistEntity> findByUserIdAndMarketOrderByIdAsc(Long userId, String market);

    /** Same-named watchlists (case-insensitive) of one user in one market, oldest first. */
    List<WatchlistEntity> findByUserIdAndMarketAndNameIgnoreCaseOrderByIdAsc(Long userId, String market, String name);
}
