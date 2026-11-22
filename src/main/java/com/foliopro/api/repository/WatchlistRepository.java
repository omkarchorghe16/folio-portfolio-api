package com.foliopro.api.repository;

import com.foliopro.api.model.WatchlistEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistEntity, Long> {
    Page<WatchlistEntity> findByMarket(String market, Pageable pageable);

    /** Oldest first, so the earliest of several same-named watchlists is found first. */
    List<WatchlistEntity> findByUserIdAndMarketOrderByIdAsc(Long userId, String market);

    /** Same-named watchlists (case-insensitive) of one user in one market, oldest first. */
    List<WatchlistEntity> findByUserIdAndMarketAndNameIgnoreCaseOrderByIdAsc(Long userId, String market, String name);
}
