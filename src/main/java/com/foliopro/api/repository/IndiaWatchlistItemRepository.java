package com.foliopro.api.repository;

import com.foliopro.api.model.IndiaWatchlistItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IndiaWatchlistItemRepository extends JpaRepository<IndiaWatchlistItemEntity, Long> {
    List<IndiaWatchlistItemEntity> findByWatchlistIdInOrderByIdAsc(Collection<Long> watchlistIds);

    Optional<IndiaWatchlistItemEntity> findByWatchlistIdAndSymbol(Long watchlistId, String symbol);

    List<IndiaWatchlistItemEntity> findByWatchlistId(Long watchlistId);

    void deleteByWatchlistId(Long watchlistId);
}
