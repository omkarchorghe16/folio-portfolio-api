package com.foliopro.api.service;

import com.foliopro.api.model.UsaWatchlistEntity;
import com.foliopro.api.model.WatchlistEntity;
import com.foliopro.api.model.WatchlistItemEntity;
import com.foliopro.api.model.WatchlistMarket;
import com.foliopro.api.repository.IndiaWatchlistItemRepository;
import com.foliopro.api.repository.UsaWatchlistItemRepository;
import com.foliopro.api.repository.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * Exports watchlists in the same column-oriented CSV format that {@link WatchlistImportService} reads: the first
 * row holds watchlist names and each column below lists that watchlist's ticker symbols, so an exported file can
 * be imported again unchanged.
 */
@Service
public class WatchlistExportService {
    private static final String LINE_END = "\r\n";
    /** Lets Excel open the file as UTF-8; the importer strips it. */
    private static final String UTF8_BOM = "﻿";
    /** Characters that make a spreadsheet read a cell as a formula. */
    static final String FORMULA_START_CHARACTERS = "=+-@\t\r";
    /** Spreadsheets show a cell that starts with an apostrophe as text and hide the apostrophe. */
    static final char PLAIN_TEXT_PREFIX = '\'';

    public record CsvExport(String fileName, String csv, int watchlists, int symbols) {
    }

    private final WatchlistRepository watchlistRepository;
    private final UsaWatchlistItemRepository usaItemRepository;
    private final IndiaWatchlistItemRepository indiaItemRepository;

    public WatchlistExportService(WatchlistRepository watchlistRepository,
                                  UsaWatchlistItemRepository usaItemRepository,
                                  IndiaWatchlistItemRepository indiaItemRepository) {
        this.watchlistRepository = watchlistRepository;
        this.usaItemRepository = usaItemRepository;
        this.indiaItemRepository = indiaItemRepository;
    }

    /**
     * @param watchlistId one watchlist to export, or null for all of the user's watchlists in the market
     * @throws NoSuchElementException when watchlistId is not one of the user's watchlists in that market
     */
    @Transactional(readOnly = true)
    public CsvExport export(String market, long userId, Long watchlistId) {
        String normalizedMarket = WatchlistMarket.normalize(market);
        List<WatchlistEntity> watchlists = watchlistRepository.findByUserIdAndMarketOrderByIdAsc(userId, normalizedMarket);
        if (watchlistId != null) {
            watchlists = watchlists.stream().filter(watchlist -> watchlist.getId().equals(watchlistId)).toList();
            if (watchlists.isEmpty()) {
                throw new NoSuchElementException("Watchlist not found");
            }
        }

        List<List<String>> columns = new ArrayList<>(watchlists.size());
        int longest = 0;
        int symbols = 0;
        for (WatchlistEntity watchlist : watchlists) {
            List<String> column = itemsOf(watchlist).stream().map(WatchlistItemEntity::getSymbol).toList();
            columns.add(column);
            longest = Math.max(longest, column.size());
            symbols += column.size();
        }

        StringBuilder csv = new StringBuilder(UTF8_BOM);
        csv.append(String.join(",", watchlists.stream().map(watchlist -> escape(watchlist.getName())).toList()));
        csv.append(LINE_END);
        for (int row = 0; row < longest; row++) {
            List<String> cells = new ArrayList<>(columns.size());
            for (List<String> column : columns) {
                cells.add(row < column.size() ? escape(column.get(row)) : "");
            }
            csv.append(String.join(",", cells)).append(LINE_END);
        }

        String scope = watchlistId != null ? watchlists.getFirst().getName() : "watchlists";
        String fileName = normalizedMarket.toLowerCase(Locale.ROOT) + "-" + fileNamePart(scope) + ".csv";
        return new CsvExport(fileName, csv.toString(), watchlists.size(), symbols);
    }

    /** Items in the order they were added, which is the order the UI lists them. */
    private List<? extends WatchlistItemEntity> itemsOf(WatchlistEntity watchlist) {
        List<? extends WatchlistItemEntity> items = watchlist instanceof UsaWatchlistEntity
                ? usaItemRepository.findByWatchlistIdInOrderByIdAsc(List.of(watchlist.getId()))
                : indiaItemRepository.findByWatchlistIdInOrderByIdAsc(List.of(watchlist.getId()));
        return items;
    }

    /**
     * Makes a cell plain text, then applies RFC 4180 quoting (cells containing a comma, quote or line break are
     * quoted, with inner quotes doubled). Spreadsheets run a cell that starts with =, +, -, @, tab or carriage
     * return as a formula (for example =HYPERLINK(...)), so such a cell gets a leading apostrophe and is shown
     * as ordinary text. {@link WatchlistImportService} removes that apostrophe again.
     */
    static String escape(String value) {
        String text = startsLikeFormula(value) ? PLAIN_TEXT_PREFIX + value : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /** True when a spreadsheet would treat the value as a formula rather than text. */
    static boolean startsLikeFormula(String value) {
        return !value.isEmpty() && FORMULA_START_CHARACTERS.indexOf(value.charAt(0)) >= 0;
    }

    /** "Long-term ideas!" -> "long-term-ideas"; falls back to "watchlist" if nothing usable is left. */
    private static String fileNamePart(String name) {
        String part = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return part.isEmpty() ? "watchlist" : part;
    }
}
