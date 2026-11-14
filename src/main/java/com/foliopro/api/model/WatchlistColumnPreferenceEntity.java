package com.foliopro.api.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** One row per selected watchlist column for a user; displayOrder is 1-based (1 = leftmost). */
@Entity
@Table(name = "watchlist_column_preferences")
@IdClass(WatchlistColumnPreferenceEntity.Key.class)
public class WatchlistColumnPreferenceEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "column_key", nullable = false, length = 50)
    private String columnKey;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WatchlistColumnPreferenceEntity() {
    }

    public WatchlistColumnPreferenceEntity(Long userId, String columnKey, Integer displayOrder,
                                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId = userId;
        this.columnKey = columnKey;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() { return userId; }
    public String getColumnKey() { return columnKey; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static class Key implements Serializable {
        private Long userId;
        private String columnKey;

        public Key() {
        }

        public Key(Long userId, String columnKey) {
            this.userId = userId;
            this.columnKey = columnKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(columnKey, key.columnKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, columnKey);
        }
    }
}
