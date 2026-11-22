package com.foliopro.api.controller;

import com.foliopro.api.model.WatchlistMarket;
import com.foliopro.api.service.WatchlistExportService;
import com.foliopro.api.service.WatchlistExportService.CsvExport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/watchlists")
@Validated
@Tag(name = "Watchlist export", description = "Download watchlists as CSV in the same format the import reads")
public class WatchlistExportController {
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final WatchlistExportService exportService;

    public WatchlistExportController(WatchlistExportService exportService) {
        this.exportService = exportService;
    }

    @Operation(summary = "Export watchlists as CSV",
            description = """
                    Column-oriented CSV, the same format POST /api/v1/watchlists/import reads: the first row holds
                    watchlist names and each column below lists that watchlist's ticker symbols in the order they
                    were added. With watchlistId, exports that one watchlist; without it, every watchlist the user
                    has in the market. Returns 404 if watchlistId is not one of the user's watchlists in that market.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV file (sent as an attachment)",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Invalid market or userId"),
            @ApiResponse(responseCode = "404", description = "Watchlist not found for this user and market")})
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportWatchlists(
            @Parameter(description = "US, USA or India", example = "India")
            @RequestParam @Pattern(regexp = WatchlistMarket.ACCEPTED_VALUES_PATTERN) String market,
            @Parameter(description = "Watchlist owner. Defaults to 1.", example = "1")
            @RequestParam(defaultValue = "1") @Positive long userId,
            @Parameter(description = "One watchlist to export; omit for all of the user's watchlists in the market")
            @RequestParam(required = false) @Positive Long watchlistId) {
        CsvExport export = exportService.export(market, userId, watchlistId);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(export.csv());
    }
}
