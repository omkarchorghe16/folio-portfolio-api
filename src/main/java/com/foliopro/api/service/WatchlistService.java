package com.foliopro.api.service;

import com.foliopro.api.dto.ApiDtos.*;
import com.foliopro.api.model.*;
import com.foliopro.api.repository.IndiaWatchlistItemRepository;
import com.foliopro.api.repository.UsaWatchlistItemRepository;
import com.foliopro.api.repository.WatchlistRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Watchlist CRUD and item management for both markets. USA and India watchlists keep their items in
 * separate tables, so the item helpers at the bottom pick the right repository for each watchlist.
 */
@Service
public class WatchlistService {
    private static final String WATCHLIST_NOT_FOUND = "Watchlist not found";
    private static final String ITEM_NOT_FOUND = "Watchlist item not found";
    /** Owner used when a request omits userId; there is no login yet. */
    private static final long DEFAULT_USER_ID = 1L;

    /** Favorites (is_favorite = true) first, then the rest; each group in creation (id) order. Same for USA and India. */
    private static final Sort WATCHLIST_ORDER = Sort.by(Sort.Order.desc("favorite"), Sort.Order.asc("id"));

    private final WatchlistRepository watchlistRepository;
    private final UsaWatchlistItemRepository usaItemRepository;
    private final IndiaWatchlistItemRepository indiaItemRepository;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            UsaWatchlistItemRepository usaItemRepository,
                            IndiaWatchlistItemRepository indiaItemRepository) {
        this.watchlistRepository = watchlistRepository;
        this.usaItemRepository = usaItemRepository;
        this.indiaItemRepository = indiaItemRepository;
    }

    // ---- Watchlists ----

    /** Returns every watchlist with all of its items, loading items in one query per market. */
    @Transactional(readOnly = true)
    public List<WatchlistResponse> listWatchlists(String market) {
        List<WatchlistEntity> watchlists = market == null
                ? watchlistRepository.findAll(WATCHLIST_ORDER)
                : watchlistRepository.findByMarket(WatchlistMarket.normalize(market), WATCHLIST_ORDER);

        List<Long> usaIds = idsOf(watchlists, UsaWatchlistEntity.class);
        List<Long> indiaIds = idsOf(watchlists, IndiaWatchlistEntity.class);
        Map<Long, List<WatchlistItemResponse>> itemsByWatchlistId = new HashMap<>();
        if (!usaIds.isEmpty()) {
            for (UsaWatchlistItemEntity item : usaItemRepository.findByWatchlistIdInOrderByIdAsc(usaIds)) {
                itemsByWatchlistId.computeIfAbsent(item.getWatchlist().getId(), key -> new ArrayList<>()).add(toItemResponse(item));
            }
        }
        if (!indiaIds.isEmpty()) {
            for (IndiaWatchlistItemEntity item : indiaItemRepository.findByWatchlistIdInOrderByIdAsc(indiaIds)) {
                itemsByWatchlistId.computeIfAbsent(item.getWatchlist().getId(), key -> new ArrayList<>()).add(toItemResponse(item));
            }
        }
        return watchlists.stream()
                .map(w -> toResponse(w, itemsByWatchlistId.getOrDefault(w.getId(), List.of())))
                .toList();
    }

    /** Returns one watchlist with all of its items, in the order they were added. */
    @Transactional(readOnly = true)
    public WatchlistResponse getWatchlist(long id) {
        return toResponse(findWatchlist(id));
    }

    @Transactional
    public WatchlistResponse createWatchlist(WatchlistRequest request) {
        String market = WatchlistMarket.normalize(request.market());
        String name = request.name().trim();
        long userId = request.userId() != null ? request.userId() : DEFAULT_USER_ID;
        Optional<WatchlistEntity> existing = findSameNamedWatchlist(userId, market, name);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        WatchlistEntity watchlist = WatchlistMarket.US.equals(market)
                ? new UsaWatchlistEntity()
                : new IndiaWatchlistEntity();
        watchlist.setName(name);
        watchlist.setUserId(userId);
        return toResponse(watchlistRepository.save(watchlist));
    }

    /** Rejects a new name that another watchlist of the same user and market already uses. */
    @Transactional
    public WatchlistResponse renameWatchlist(long id, WatchlistRequest request) {
        WatchlistEntity watchlist = findWatchlist(id);
        if (!watchlist.getMarket().equals(WatchlistMarket.normalize(request.market()))) {
            throw new IllegalArgumentException("Market cannot be changed when renaming a watchlist");
        }
        String name = request.name().trim();
        boolean nameTakenByAnother = findSameNamedWatchlist(watchlist.getUserId(), watchlist.getMarket(), name)
                .filter(other -> !other.getId().equals(watchlist.getId()))
                .isPresent();
        if (nameTakenByAnother) {
            throw new IllegalArgumentException("A watchlist named \"" + name + "\" already exists");
        }
        watchlist.setName(name);
        return toResponse(watchlistRepository.save(watchlist));
    }

    /** Stars or un-stars a watchlist (USA or India). Watchlists owned by another user are reported as not found. */
    @Transactional
    public WatchlistResponse setWatchlistFavorite(long id, long userId, boolean isFavorite) {
        WatchlistEntity watchlist = watchlistRepository.findById(id)
                .filter(found -> found.getUserId() != null && found.getUserId() == userId)
                .orElseThrow(() -> new NoSuchElementException(WATCHLIST_NOT_FOUND));
        watchlist.setFavorite(isFavorite);
        return toResponse(watchlistRepository.save(watchlist));
    }

    @Transactional
    public void deleteWatchlist(long id) {
        WatchlistEntity watchlist = findWatchlist(id);
        deleteAllItems(watchlist);
        watchlistRepository.delete(watchlist);
    }

    // ---- Watchlist items ----

    /** Adds all items or none: a symbol repeated in the request or already in the watchlist is rejected. */
    @Transactional
    public List<WatchlistItemResponse> addItems(long watchlistId, BulkItemsRequest request) {
        return addItemsTo(findWatchlist(watchlistId), request);
    }

    /**
     * Adds stocks to an India watchlist owned by {@code userId}, all or none. USA watchlists and watchlists
     * owned by another user are reported as not found, so this endpoint can never write to watchlist_items_usa.
     */
    @Transactional
    public List<WatchlistItemResponse> addIndiaItems(long watchlistId, long userId, BulkItemsRequest request) {
        return addItemsTo(findOwnedWatchlist(watchlistId, userId, IndiaWatchlistEntity.class), request);
    }

    /** Same as {@link #addIndiaItems} for a USA watchlist (watchlist_items_usa); India lists are not found. */
    @Transactional
    public List<WatchlistItemResponse> addUsaItems(long watchlistId, long userId, BulkItemsRequest request) {
        return addItemsTo(findOwnedWatchlist(watchlistId, userId, UsaWatchlistEntity.class), request);
    }

    private List<WatchlistItemResponse> addItemsTo(WatchlistEntity watchlist, BulkItemsRequest request) {
        Set<String> seenSymbols = new HashSet<>();
        List<WatchlistItemResponse> added = new ArrayList<>();
        for (WatchlistItemRequest itemRequest : request.items()) {
            String symbol = normalizeSymbol(itemRequest.symbol());
            if (!seenSymbols.add(symbol) || findItem(watchlist, symbol).isPresent()) {
                throw new IllegalArgumentException("Duplicate symbol in watchlist: " + symbol);
            }
            WatchlistItemEntity item = newItemFor(watchlist);
            applyRequest(item, itemRequest);
            added.add(toItemResponse(saveItem(item)));
        }
        return added;
    }

    @Transactional
    public WatchlistItemResponse updateItem(long watchlistId, String symbol, WatchlistItemRequest request) {
        WatchlistEntity watchlist = findWatchlist(watchlistId);
        WatchlistItemEntity item = findItem(watchlist, normalizeSymbol(symbol))
                .orElseThrow(() -> new NoSuchElementException(ITEM_NOT_FOUND));
        boolean symbolChanged = !item.getSymbol().equalsIgnoreCase(request.symbol());
        if (symbolChanged && findItem(watchlist, normalizeSymbol(request.symbol())).isPresent()) {
            throw new IllegalArgumentException("Duplicate symbol in watchlist");
        }
        applyRequest(item, request);
        return toItemResponse(saveItem(item));
    }

    @Transactional
    public void removeItem(long watchlistId, String symbol) {
        WatchlistEntity watchlist = findWatchlist(watchlistId);
        WatchlistItemEntity item = findItem(watchlist, normalizeSymbol(symbol))
                .orElseThrow(() -> new NoSuchElementException(ITEM_NOT_FOUND));
        deleteItem(item);
    }

    /** Removes one stock from an India watchlist owned by {@code userId}; USA and other users' lists are not found. */
    @Transactional
    public void removeIndiaItem(long watchlistId, long userId, String symbol) {
        removeOwnedItem(findOwnedWatchlist(watchlistId, userId, IndiaWatchlistEntity.class), symbol);
    }

    /** Removes one stock from a USA watchlist owned by {@code userId}; India and other users' lists are not found. */
    @Transactional
    public void removeUsaItem(long watchlistId, long userId, String symbol) {
        removeOwnedItem(findOwnedWatchlist(watchlistId, userId, UsaWatchlistEntity.class), symbol);
    }

    private void removeOwnedItem(WatchlistEntity watchlist, String symbol) {
        WatchlistItemEntity item = findItem(watchlist, normalizeSymbol(symbol))
                .orElseThrow(() -> new NoSuchElementException(ITEM_NOT_FOUND));
        deleteItem(item);
    }

    // ---- Helpers ----

    /** A watchlist of the given market owned by {@code userId}; anything else is reported as not found. */
    private WatchlistEntity findOwnedWatchlist(long id, long userId, Class<? extends WatchlistEntity> market) {
        return watchlistRepository.findById(id)
                .filter(market::isInstance)
                .filter(found -> found.getUserId() != null && found.getUserId() == userId)
                .orElseThrow(() -> new NoSuchElementException(WATCHLIST_NOT_FOUND));
    }

    /** The oldest of the user's watchlists in that market with this name, ignoring case. */
    private Optional<WatchlistEntity> findSameNamedWatchlist(long userId, String market, String name) {
        return watchlistRepository.findByUserIdAndMarketAndNameIgnoreCaseOrderByIdAsc(userId, market, name)
                .stream()
                .findFirst();
    }

    private WatchlistEntity findWatchlist(long id) {
        return watchlistRepository.findById(id).orElseThrow(() -> new NoSuchElementException(WATCHLIST_NOT_FOUND));
    }

    private static List<Long> idsOf(List<WatchlistEntity> watchlists, Class<? extends WatchlistEntity> type) {
        return watchlists.stream().filter(type::isInstance).map(WatchlistEntity::getId).toList();
    }

    private static String normalizeSymbol(String symbol) {
        return symbol.toUpperCase(Locale.ROOT);
    }

    private void applyRequest(WatchlistItemEntity item, WatchlistItemRequest request) {
        item.setSymbol(request.symbol());
        boolean hasName = request.name() != null && !request.name().isBlank();
        item.setName(hasName ? request.name().trim() : normalizeSymbol(request.symbol()));
        item.setFavorite(Boolean.TRUE.equals(request.isFavorite()));
    }

    // Market-specific item storage: USA and India items live in separate tables.

    private WatchlistItemEntity newItemFor(WatchlistEntity watchlist) {
        if (watchlist instanceof UsaWatchlistEntity usa) {
            UsaWatchlistItemEntity item = new UsaWatchlistItemEntity();
            item.setWatchlist(usa);
            return item;
        }
        IndiaWatchlistItemEntity item = new IndiaWatchlistItemEntity();
        item.setWatchlist((IndiaWatchlistEntity) watchlist);
        return item;
    }

    private WatchlistItemEntity saveItem(WatchlistItemEntity item) {
        if (item instanceof UsaWatchlistItemEntity usa) {
            return usaItemRepository.save(usa);
        }
        return indiaItemRepository.save((IndiaWatchlistItemEntity) item);
    }

    private Optional<WatchlistItemEntity> findItem(WatchlistEntity watchlist, String symbol) {
        if (watchlist instanceof UsaWatchlistEntity) {
            return usaItemRepository.findByWatchlistIdAndSymbol(watchlist.getId(), symbol).map(WatchlistItemEntity.class::cast);
        }
        return indiaItemRepository.findByWatchlistIdAndSymbol(watchlist.getId(), symbol).map(WatchlistItemEntity.class::cast);
    }

    private List<WatchlistItemEntity> findAllItems(WatchlistEntity watchlist) {
        if (watchlist instanceof UsaWatchlistEntity) {
            return usaItemRepository.findByWatchlistId(watchlist.getId()).stream().map(WatchlistItemEntity.class::cast).toList();
        }
        return indiaItemRepository.findByWatchlistId(watchlist.getId()).stream().map(WatchlistItemEntity.class::cast).toList();
    }

    private void deleteItem(WatchlistItemEntity item) {
        if (item instanceof UsaWatchlistItemEntity usa) {
            usaItemRepository.delete(usa);
        } else {
            indiaItemRepository.delete((IndiaWatchlistItemEntity) item);
        }
    }

    private void deleteAllItems(WatchlistEntity watchlist) {
        if (watchlist instanceof UsaWatchlistEntity) {
            usaItemRepository.deleteByWatchlistId(watchlist.getId());
        } else {
            indiaItemRepository.deleteByWatchlistId(watchlist.getId());
        }
    }

    // ---- Mapping ----

    private WatchlistResponse toResponse(WatchlistEntity watchlist) {
        return toResponse(watchlist, findAllItems(watchlist).stream().map(this::toItemResponse).toList());
    }

    private WatchlistResponse toResponse(WatchlistEntity watchlist, List<WatchlistItemResponse> items) {
        return new WatchlistResponse(watchlist.getId(), watchlist.getName(), watchlist.getMarket(),
                watchlist.getCurrency(), watchlist.getUserId(), watchlist.isFavorite(), items);
    }

    private WatchlistItemResponse toItemResponse(WatchlistItemEntity item) {
        return new WatchlistItemResponse(item.getId(), item.getSymbol(), item.getName(), item.isFavorite());
    }
}
