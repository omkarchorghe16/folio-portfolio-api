package com.foliopro.api.service;

import com.foliopro.api.dto.ApiDtos.WatchlistImportResponse;
import com.foliopro.api.model.IndiaWatchlistEntity;
import com.foliopro.api.model.IndiaWatchlistItemEntity;
import com.foliopro.api.model.UsaWatchlistEntity;
import com.foliopro.api.model.UsaWatchlistItemEntity;
import com.foliopro.api.model.WatchlistEntity;
import com.foliopro.api.model.WatchlistItemEntity;
import com.foliopro.api.model.WatchlistMarket;
import com.foliopro.api.repository.IndiaWatchlistItemRepository;
import com.foliopro.api.repository.UsaWatchlistItemRepository;
import com.foliopro.api.repository.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Imports column-oriented watchlist CSV files. Imports only ever add: a column whose name matches one
 * of the user's existing watchlists in the same market (ignoring case and surrounding spaces) adds its
 * new symbols to that watchlist, and any other column creates a new watchlist. Existing watchlists and
 * stocks are never removed or renamed, and symbols already in a watchlist are skipped.
 */
@Service
public class WatchlistImportService {
    private static final int MAX_WATCHLIST_NAME_LENGTH = 40;
    private static final int MAX_SYMBOL_LENGTH = 15;
    /** Same rule as items added through the API: '.', '-', '&' (M&M) and '$' (US preferreds such as ABR$D). */
    private static final String SYMBOL_PATTERN = com.foliopro.api.dto.ApiDtos.WatchlistItemRequest.SYMBOL_PATTERN;

    private final WatchlistRepository watchlistRepository;
    private final UsaWatchlistItemRepository usaItemRepository;
    private final IndiaWatchlistItemRepository indiaItemRepository;

    public WatchlistImportService(WatchlistRepository watchlistRepository,
                                  UsaWatchlistItemRepository usaItemRepository,
                                  IndiaWatchlistItemRepository indiaItemRepository) {
        this.watchlistRepository = watchlistRepository;
        this.usaItemRepository = usaItemRepository;
        this.indiaItemRepository = indiaItemRepository;
    }

    /** Validates the whole file first, then saves everything in one transaction or nothing. */
    @Transactional
    public WatchlistImportResponse importWatchlists(MultipartFile file, String market, Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("User ID must be a positive number");
        }
        String normalizedMarket = WatchlistMarket.normalize(market);
        List<WatchlistData> parsed = parse(readCsv(file));
        Map<String, WatchlistEntity> existingByName = findExistingWatchlistsByName(userId, normalizedMarket);

        int watchlistsCreated = 0;
        int watchlistsUpdated = 0;
        int itemsCreated = 0;
        int itemsSkipped = 0;
        List<String> watchlistNames = new ArrayList<>(parsed.size());
        for (WatchlistData data : parsed) {
            WatchlistEntity watchlist = existingByName.get(nameKey(data.name()));
            Set<String> existingSymbols;
            if (watchlist == null) {
                watchlist = createWatchlist(data.name(), normalizedMarket, userId);
                existingSymbols = new HashSet<>();
                watchlistsCreated++;
            } else {
                existingSymbols = findExistingSymbols(watchlist);
                watchlistsUpdated++;
            }
            watchlistNames.add(watchlist.getName());

            for (String symbol : data.symbols()) {
                if (existingSymbols.contains(symbol)) {
                    itemsSkipped++;
                } else {
                    addItem(watchlist, symbol);
                    existingSymbols.add(symbol);
                    itemsCreated++;
                }
            }
        }
        return new WatchlistImportResponse(watchlistsCreated, watchlistsUpdated, itemsCreated, itemsSkipped,
                watchlistNames);
    }

    private String readCsv(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read CSV file", e);
        }
    }

    /** The user's watchlists in one market, keyed by name. If names repeat, the oldest watchlist wins. */
    private Map<String, WatchlistEntity> findExistingWatchlistsByName(Long userId, String market) {
        Map<String, WatchlistEntity> byName = new HashMap<>();
        for (WatchlistEntity watchlist : watchlistRepository.findByUserIdAndMarketOrderByIdAsc(userId, market)) {
            byName.putIfAbsent(nameKey(watchlist.getName()), watchlist);
        }
        return byName;
    }

    private static String nameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private WatchlistEntity createWatchlist(String name, String market, Long userId) {
        WatchlistEntity watchlist = WatchlistMarket.US.equals(market)
                ? new UsaWatchlistEntity()
                : new IndiaWatchlistEntity();
        watchlist.setUserId(userId);
        watchlist.setName(name);
        return watchlistRepository.save(watchlist);
    }

    /** Symbols already in the watchlist, upper-cased so they compare with the normalized CSV symbols. */
    private Set<String> findExistingSymbols(WatchlistEntity watchlist) {
        List<? extends WatchlistItemEntity> items = watchlist instanceof UsaWatchlistEntity
                ? usaItemRepository.findByWatchlistId(watchlist.getId())
                : indiaItemRepository.findByWatchlistId(watchlist.getId());
        Set<String> symbols = new HashSet<>();
        for (WatchlistItemEntity item : items) {
            symbols.add(item.getSymbol().toUpperCase(Locale.ROOT));
        }
        return symbols;
    }

    private void addItem(WatchlistEntity watchlist, String symbol) {
        if (watchlist instanceof UsaWatchlistEntity usa) {
            UsaWatchlistItemEntity item = new UsaWatchlistItemEntity();
            item.setWatchlist(usa);
            item.setSymbol(symbol);
            item.setName(symbol);
            usaItemRepository.save(item);
        } else {
            IndiaWatchlistItemEntity item = new IndiaWatchlistItemEntity();
            item.setWatchlist((IndiaWatchlistEntity) watchlist);
            item.setSymbol(symbol);
            item.setName(symbol);
            indiaItemRepository.save(item);
        }
    }

    private List<WatchlistData> parse(String csv) {
        List<List<String>> rows = parseRows(csv);
        List<List<String>> nonEmptyRows = rows.stream()
                .filter(row -> row.stream().anyMatch(cell -> !cell.isBlank()))
                .toList();
        if (nonEmptyRows.isEmpty()) {
            throw new IllegalArgumentException("CSV must contain watchlist names in its first row");
        }

        List<String> headers = nonEmptyRows.getFirst();
        List<WatchlistData> watchlistData = new ArrayList<>(headers.size());
        Set<String> names = new HashSet<>();
        for (String header : headers) {
            String name = withoutPlainTextPrefix(header.trim());
            if (name.isEmpty() || name.length() > MAX_WATCHLIST_NAME_LENGTH) {
                throw new IllegalArgumentException("Watchlist names must contain 1 to "
                        + MAX_WATCHLIST_NAME_LENGTH + " characters");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate watchlist name: " + name);
            }
            watchlistData.add(new WatchlistData(name, new ArrayList<>(), new HashSet<>()));
        }

        for (int rowIndex = 1; rowIndex < nonEmptyRows.size(); rowIndex++) {
            List<String> row = nonEmptyRows.get(rowIndex);
            if (row.size() > headers.size()) {
                throw new IllegalArgumentException("CSV row " + (rowIndex + 1)
                        + " has more columns than the watchlist header row");
            }
            for (int column = 0; column < row.size(); column++) {
                String symbol = withoutPlainTextPrefix(row.get(column).trim());
                if (symbol.isEmpty()) {
                    continue;
                }
                if (symbol.length() > MAX_SYMBOL_LENGTH || !symbol.matches(SYMBOL_PATTERN)) {
                    throw new IllegalArgumentException("Invalid stock symbol at row "
                            + (rowIndex + 1) + ", column " + (column + 1));
                }
                WatchlistData list = watchlistData.get(column);
                String normalized = symbol.toUpperCase(Locale.ROOT);
                if (!list.seenSymbols().add(normalized)) {
                    throw new IllegalArgumentException("Duplicate symbol in watchlist "
                            + list.name() + ": " + normalized);
                }
                list.symbols().add(normalized);
            }
        }
        return watchlistData;
    }

    private List<List<String>> parseRows(String csv) {
        if (csv.startsWith("\uFEFF")) {
            csv = csv.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterQuote = false;
        boolean recordStarted = false;

        for (int i = 0; i < csv.length(); i++) {
            char current = csv.charAt(i);
            if (inQuotes) {
                if (current == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(current);
                }
            } else if (afterQuote) {
                if (current == ',') {
                    addField(row, field);
                    afterQuote = false;
                    recordStarted = true;
                } else if (current == '\n' || current == '\r') {
                    addField(row, field);
                    rows.add(row);
                    row = new ArrayList<>();
                    afterQuote = false;
                    recordStarted = false;
                    if (current == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                        i++;
                    }
                } else {
                    throw new IllegalArgumentException("Malformed CSV: unexpected character after a quoted field");
                }
            } else if (current == '"') {
                if (!field.isEmpty()) {
                    throw new IllegalArgumentException("Malformed CSV: quote inside an unquoted field");
                }
                inQuotes = true;
                recordStarted = true;
            } else if (current == ',') {
                addField(row, field);
                recordStarted = true;
            } else if (current == '\n' || current == '\r') {
                addField(row, field);
                rows.add(row);
                row = new ArrayList<>();
                recordStarted = false;
                if (current == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(current);
                recordStarted = true;
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Malformed CSV: unterminated quoted field");
        }
        if (recordStarted || afterQuote || !row.isEmpty() || !field.isEmpty()) {
            addField(row, field);
            rows.add(row);
        }
        return rows;
    }

    /**
     * Undoes the export's formula protection: "'=SUM(A1)" becomes "=SUM(A1)" again, so an exported file
     * imports unchanged. An apostrophe before any other character is kept.
     */
    private static String withoutPlainTextPrefix(String cell) {
        if (cell.length() > 1 && cell.charAt(0) == WatchlistExportService.PLAIN_TEXT_PREFIX
                && WatchlistExportService.startsLikeFormula(cell.substring(1))) {
            return cell.substring(1);
        }
        return cell;
    }

    private void addField(List<String> row, StringBuilder field) {
        row.add(field.toString());
        field.setLength(0);
    }

    private record WatchlistData(String name, List<String> symbols, Set<String> seenSymbols) {}
}
