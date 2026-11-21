package com.foliopro.api.service;

import com.foliopro.api.dto.WatchlistColumnPreferenceDtos.*;
import com.foliopro.api.model.WatchlistColumnPreferenceEntity;
import com.foliopro.api.repository.WatchlistColumnPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WatchlistColumnPreferencesService {
    private final WatchlistColumnPreferenceRepository repository;

    public WatchlistColumnPreferencesService(WatchlistColumnPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WatchlistColumnPreferencesResponse getPreferences(Long userId) {
        List<WatchlistColumnPreferenceEntity> rows = repository.findByUserIdOrderByDisplayOrderAsc(userId);
        if (rows.isEmpty()) {
            throw new NoSuchElementException("No watchlist column preferences saved");
        }
        return toResponse(userId, rows);
    }

    /** Full replace: the request becomes the user's complete column selection, stored contiguously 1..N. */
    @Transactional
    public WatchlistColumnPreferencesResponse replacePreferences(Long userId, WatchlistColumnPreferencesRequest request) {
        List<WatchlistColumnPreferenceDto> columns = request.columns();
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (WatchlistColumnPreferenceDto column : columns) {
            if (!keys.add(column.columnKey())) {
                throw new IllegalArgumentException("Duplicate column key: " + column.columnKey());
            }
            if (!orders.add(column.displayOrder())) {
                throw new IllegalArgumentException("Duplicate display order: " + column.displayOrder());
            }
        }

        repository.deleteByUserId(userId);
        repository.flush();

        List<WatchlistColumnPreferenceDto> sorted = columns.stream()
                .sorted(Comparator.comparing(WatchlistColumnPreferenceDto::displayOrder))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        List<WatchlistColumnPreferenceEntity> entities = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            entities.add(new WatchlistColumnPreferenceEntity(userId, sorted.get(i).columnKey(), i + 1, now, now));
        }
        repository.saveAllAndFlush(entities);

        return toResponse(userId, repository.findByUserIdOrderByDisplayOrderAsc(userId));
    }

    private WatchlistColumnPreferencesResponse toResponse(Long userId, List<WatchlistColumnPreferenceEntity> rows) {
        List<WatchlistColumnPreferenceDto> columns = rows.stream()
                .map(row -> new WatchlistColumnPreferenceDto(row.getColumnKey(), row.getDisplayOrder()))
                .toList();
        LocalDateTime updatedAt = rows.stream()
                .map(WatchlistColumnPreferenceEntity::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new WatchlistColumnPreferencesResponse(userId, columns, updatedAt);
    }
}
