package com.foliopro.api.repository;

import com.foliopro.api.model.WatchlistColumnPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WatchlistColumnPreferenceRepository
        extends JpaRepository<WatchlistColumnPreferenceEntity, WatchlistColumnPreferenceEntity.Key> {

    List<WatchlistColumnPreferenceEntity> findByUserIdOrderByDisplayOrderAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WatchlistColumnPreferenceEntity p WHERE p.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
