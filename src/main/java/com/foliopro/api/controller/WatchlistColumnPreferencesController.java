package com.foliopro.api.controller;

import com.foliopro.api.dto.WatchlistColumnPreferenceDtos.*;
import com.foliopro.api.service.WatchlistColumnPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{userId}/watchlist-column-preferences")
@Validated
@Tag(name = "Watchlist column preferences",
        description = "Per-user selection and left-to-right order of watchlist table columns")
public class WatchlistColumnPreferencesController {
    private final WatchlistColumnPreferencesService service;

    public WatchlistColumnPreferencesController(WatchlistColumnPreferencesService service) {
        this.service = service;
    }

    @Operation(summary = "Get watchlist column preferences",
            description = "Returns the user's selected columns sorted by displayOrder (1 = leftmost). "
                    + "Responds 404 when the user has not saved any preferences; clients should fall back to defaults.")
    @GetMapping
    public WatchlistColumnPreferencesResponse get(@PathVariable @Positive Long userId) {
        return service.get(userId);
    }

    @Operation(summary = "Replace watchlist column preferences",
            description = "Replaces the user's full column selection. Column keys and display orders must be unique. "
                    + "Orders are normalized to a contiguous 1..N sequence (e.g. 2,5 becomes 1,2).")
    @PutMapping
    public WatchlistColumnPreferencesResponse replace(@PathVariable @Positive Long userId,
                                                      @Valid @RequestBody WatchlistColumnPreferencesRequest request) {
        return service.replace(userId, request);
    }
}
