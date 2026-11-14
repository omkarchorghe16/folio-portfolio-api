package com.foliopro.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public final class WatchlistColumnPreferenceDtos {
    private WatchlistColumnPreferenceDtos() {}

    public record WatchlistColumnPreferenceDto(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_]+") String columnKey,
            @NotNull @Min(1) Integer displayOrder) {}

    public record WatchlistColumnPreferencesRequest(
            @NotEmpty @Size(max = 100) List<@Valid @NotNull WatchlistColumnPreferenceDto> columns) {}

    /** columns are sorted by displayOrder ascending; updatedAt is the latest updated_at among the rows. */
    public record WatchlistColumnPreferencesResponse(
            Long userId, List<WatchlistColumnPreferenceDto> columns, LocalDateTime updatedAt) {}
}
