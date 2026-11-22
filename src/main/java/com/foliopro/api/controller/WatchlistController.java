package com.foliopro.api.controller;

import com.foliopro.api.dto.ApiDtos.*;
import com.foliopro.api.model.WatchlistMarket;
import com.foliopro.api.service.WatchlistService;
import com.foliopro.api.service.WatchlistImportService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlists")
@Validated
@Tag(name = "Watchlists", description = "Watchlist CRUD, items, pagination, and CSV import")
public class WatchlistController {
    private static final String SYMBOL_PATTERN = WatchlistItemRequest.SYMBOL_PATTERN;
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private final WatchlistService watchlistService;
    private final WatchlistImportService importService;

    public WatchlistController(WatchlistService watchlistService, WatchlistImportService importService) {
        this.watchlistService = watchlistService;
        this.importService = importService;
    }

    @Operation(summary = "List watchlists with tickers",
            description = "Returns a page of watchlists with every ticker and its name nested in each watchlist's items array. Pagination applies to watchlists; market accepts US, USA or India, and omitting it includes both.")
    @GetMapping
    public PageResponse<WatchlistResponse> listWatchlists(
            @RequestParam(required = false) @Pattern(regexp = WatchlistMarket.ACCEPTED_VALUES_PATTERN) String market,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "15") @Min(1) @Max(100) int size) {
        return watchlistService.listWatchlists(market, page, size);
    }

    @Operation(summary = "Create a watchlist",
            description = "Creates an empty watchlist for the selected market (US, USA or India). userId defaults to 1 if omitted. "
                    + "If the user already has a watchlist with the same name in that market (case-insensitive), "
                    + "that existing watchlist is returned and no new one is created.")
    @PostMapping
    public WatchlistResponse createWatchlist(@Valid @RequestBody WatchlistRequest request) {
        return watchlistService.createWatchlist(request);
    }

    @Operation(summary = "Get a watchlist", description = "Returns a watchlist with a page of its items.")
    @GetMapping("/{id}")
    public WatchlistResponse getWatchlist(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "15") @Min(1) @Max(100) int size) {
        return watchlistService.getWatchlist(id, page, size);
    }

    @Operation(summary = "Rename a watchlist",
            description = "Updates the watchlist name. Its market cannot be changed, and the name must not be used by "
                    + "another of the owner's watchlists in that market (400).")
    @PatchMapping("/{id}")
    public WatchlistResponse renameWatchlist(
            @PathVariable @Positive long id, @Valid @RequestBody WatchlistRequest request) {
        return watchlistService.renameWatchlist(id, request);
    }

    @Operation(summary = "Mark or unmark a watchlist as favorite",
            description = "Sets is_favorite on a USA or India watchlist owned by userId (defaults to 1). Returns 404 if the watchlist does not exist or belongs to another user.")
    @PatchMapping("/{id}/favorite")
    public WatchlistResponse setWatchlistFavorite(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Positive long userId,
            @Valid @RequestBody WatchlistFavoriteRequest request) {
        return watchlistService.setWatchlistFavorite(id, userId, request.isFavorite());
    }

    @Operation(summary = "Delete a watchlist", description = "Deletes the watchlist and its items.")
    @DeleteMapping("/{id}")
    public void deleteWatchlist(@PathVariable @Positive long id) {
        watchlistService.deleteWatchlist(id);
    }

    @Operation(summary = "Add items to a watchlist",
            description = "Adds up to 100 items atomically. Duplicate symbols in a list are rejected.")
    @PostMapping("/{id}/items")
    public List<WatchlistItemResponse> addItems(
            @PathVariable @Positive long id, @Valid @RequestBody BulkItemsRequest request) {
        return watchlistService.addItems(id, request);
    }

    @Operation(summary = "Add stocks to an India watchlist",
            description = "Saves up to 100 NSE stocks into watchlist_items_india for an India watchlist owned by userId "
                    + "(defaults to 1). All items are saved or none: a symbol repeated in the request or already in "
                    + "the watchlist returns 400. Returns 404 if the watchlist does not exist, is a USA watchlist, or "
                    + "belongs to another user.")
    @PostMapping("/india/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public List<WatchlistItemResponse> addIndiaItems(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Positive long userId,
            @Valid @RequestBody BulkItemsRequest request) {
        return watchlistService.addIndiaItems(id, userId, request);
    }

    @Operation(summary = "Remove a stock from an India watchlist",
            description = "Deletes one row from watchlist_items_india for an India watchlist owned by userId "
                    + "(defaults to 1). The symbol is matched case-insensitively. Returns 204 when deleted and 404 if "
                    + "the watchlist does not exist, is a USA watchlist, belongs to another user, or does not "
                    + "contain the symbol.")
    @DeleteMapping("/india/{id}/items/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeIndiaItem(
            @PathVariable @Positive long id,
            @PathVariable @Pattern(regexp = SYMBOL_PATTERN) String symbol,
            @RequestParam(defaultValue = "1") @Positive long userId) {
        watchlistService.removeIndiaItem(id, userId, symbol);
    }

    @Operation(summary = "Add stocks to a USA watchlist",
            description = "Saves up to 100 US stocks into watchlist_items_usa for a USA watchlist owned by userId "
                    + "(defaults to 1). All items are saved or none: a symbol repeated in the request or already in "
                    + "the watchlist returns 400. Returns 404 if the watchlist does not exist, is an India watchlist, "
                    + "or belongs to another user.")
    @PostMapping("/us/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public List<WatchlistItemResponse> addUsaItems(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Positive long userId,
            @Valid @RequestBody BulkItemsRequest request) {
        return watchlistService.addUsaItems(id, userId, request);
    }

    @Operation(summary = "Remove a stock from a USA watchlist",
            description = "Deletes one row from watchlist_items_usa for a USA watchlist owned by userId (defaults to "
                    + "1). The symbol is matched case-insensitively. Returns 204 when deleted and 404 if the "
                    + "watchlist does not exist, is an India watchlist, belongs to another user, or does not "
                    + "contain the symbol.")
    @DeleteMapping("/us/{id}/items/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUsaItem(
            @PathVariable @Positive long id,
            @PathVariable @Pattern(regexp = SYMBOL_PATTERN) String symbol,
            @RequestParam(defaultValue = "1") @Positive long userId) {
        watchlistService.removeUsaItem(id, userId, symbol);
    }

    @Operation(summary = "Update a watchlist item", description = "Updates an item identified by its symbol.")
    @PutMapping("/{id}/items/{symbol}")
    public WatchlistItemResponse updateItem(
            @PathVariable @Positive long id,
            @PathVariable String symbol,
            @Valid @RequestBody WatchlistItemRequest request) {
        return watchlistService.updateItem(id, symbol, request);
    }

    @Operation(summary = "Remove an item from a watchlist")
    @DeleteMapping("/{id}/items/{symbol}")
    public void removeItem(@PathVariable @Positive long id, @PathVariable String symbol) {
        watchlistService.removeItem(id, symbol);
    }

    @Operation(
            summary = "Import watchlists from CSV",
            description = """
                    Upload a column-oriented CSV file. The first row contains watchlist names; each subsequent
                    column contains the stock symbols for that watchlist. Empty cells are ignored. Names and
                    symbols must be unique within the file. The complete file is validated before any records
                    are saved. Maximum file size is 5 MB.

                    Imports only add data. If the user already has a watchlist with the same name in the same
                    market (case-insensitive), its new symbols are added to that watchlist and symbols it
                    already contains are skipped. Other names create new watchlists. Existing watchlists and
                    stocks are never deleted.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "object", requiredProperties = {"file"})
                    )
            )
    )
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistImportResponse importWatchlists(
            @Parameter(description = "Column-oriented watchlist CSV file", required = true,
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file,
             @Parameter(description = "Target market: US, USA or India (case-insensitive)", example = "USA",
                     schema = @Schema(allowableValues = {"USA", "India", "US"}, defaultValue = "USA"))
            @RequestParam(defaultValue = "USA") String market,
            @Parameter(description = "Watchlist owner ID (USA and India). Defaults to 1.", example = "1")
            @RequestParam(defaultValue = "1") Long userId) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV file must not exceed 5 MB");
        }
        return importService.importWatchlists(file, market, userId);
    }
}
