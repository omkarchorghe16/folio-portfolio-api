package com.foliopro.api.service;

import com.foliopro.api.dto.StockHoldingDtos.StockHoldingPositionRequest;
import com.foliopro.api.dto.StockHoldingDtos.StockHoldingRequest;
import com.foliopro.api.dto.StockHoldingDtos.StockHoldingResponse;
import com.foliopro.api.model.IndiaStockHoldingEntity;
import com.foliopro.api.model.StockHoldingEntity;
import com.foliopro.api.model.UsaStockHoldingEntity;
import com.foliopro.api.model.WatchlistMarket;
import com.foliopro.api.repository.IndiaStockHoldingRepository;
import com.foliopro.api.repository.StockHoldingRepository;
import com.foliopro.api.repository.UsaStockHoldingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Stocks and ETFs a user holds, kept in separate USA and India tables. Prices are read elsewhere. */
@Service
public class StockHoldingService {
    private static final String HOLDING_NOT_FOUND = "Stock holding not found";

    /** What folio-market-feed-svc accepts for FMP profiles; one other symbol would fail the whole request. */
    private static final Pattern USA_SYMBOL = Pattern.compile("[A-Z0-9][A-Z0-9.-]{0,19}");
    /** NSE symbols, e.g. RELIANCE, M&M, BAJAJ-AUTO. */
    private static final Pattern INDIA_SYMBOL = Pattern.compile("[A-Z0-9][A-Z0-9&._-]{0,19}");
    private static final Pattern INDIA_SUFFIX = Pattern.compile("\\.(NS|BO)$");

    private final UsaStockHoldingRepository usaStockHoldingRepository;
    private final IndiaStockHoldingRepository indiaStockHoldingRepository;

    public StockHoldingService(UsaStockHoldingRepository usaStockHoldingRepository,
                               IndiaStockHoldingRepository indiaStockHoldingRepository) {
        this.usaStockHoldingRepository = usaStockHoldingRepository;
        this.indiaStockHoldingRepository = indiaStockHoldingRepository;
    }

    @Transactional(readOnly = true)
    public List<StockHoldingResponse> listHoldings(Long userId, String market) {
        return repositoryOf(market).findByUserIdOrderBySymbolAsc(userId).stream()
                .map(StockHoldingService::toResponse)
                .toList();
    }

    /** Fails with 409 when the user already holds the symbol in this market. */
    @Transactional
    public StockHoldingResponse createHolding(Long userId, String market, StockHoldingRequest request) {
        String symbol = normalizeSymbol(request.symbol(), market);
        return WatchlistMarket.isUs(market)
                ? saveNewHolding(usaStockHoldingRepository, UsaStockHoldingEntity::new, userId, symbol, request)
                : saveNewHolding(indiaStockHoldingRepository, IndiaStockHoldingEntity::new, userId, symbol, request);
    }

    @Transactional
    public StockHoldingResponse updateHoldingPosition(Long userId, String market, Long id,
                                                      StockHoldingPositionRequest request) {
        StockHoldingEntity holding = findHolding(repositoryOf(market), userId, id);
        holding.setPosition(request.quantity(), request.averageCost());
        return toResponse(holding);
    }

    @Transactional
    public void deleteHolding(Long userId, String market, Long id) {
        deleteOwnedHolding(repositoryOf(market), userId, id);
    }

    private <T extends StockHoldingEntity> StockHoldingResponse saveNewHolding(
            StockHoldingRepository<T> repository, Supplier<T> newHolding, Long userId, String symbol,
            StockHoldingRequest request) {
        if (repository.existsByUserIdAndSymbol(userId, symbol)) {
            throw alreadyHeld(symbol);
        }
        T holding = newHolding.get();
        holding.setUserId(userId);
        holding.setSymbol(symbol);
        holding.setName(request.name().trim());
        holding.setAssetType(request.assetType());
        holding.setPosition(request.quantity(), request.averageCost());
        try {
            return toResponse(repository.saveAndFlush(holding));
        } catch (DataIntegrityViolationException exception) {
            // Two requests added the same symbol at the same time; the unique constraint stopped the second.
            throw alreadyHeld(symbol);
        }
    }

    private static <T extends StockHoldingEntity> void deleteOwnedHolding(StockHoldingRepository<T> repository,
                                                                          Long userId, Long id) {
        repository.delete(findHolding(repository, userId, id));
    }

    private static <T extends StockHoldingEntity> T findHolding(StockHoldingRepository<T> repository,
                                                                Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException(HOLDING_NOT_FOUND));
    }

    private StockHoldingRepository<? extends StockHoldingEntity> repositoryOf(String market) {
        return WatchlistMarket.isUs(market) ? usaStockHoldingRepository : indiaStockHoldingRepository;
    }

    private static String normalizeSymbol(String rawSymbol, String market) {
        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        boolean isUs = WatchlistMarket.isUs(market);
        if (!isUs) {
            symbol = INDIA_SUFFIX.matcher(symbol).replaceFirst("");
        }
        if (!(isUs ? USA_SYMBOL : INDIA_SYMBOL).matcher(symbol).matches()) {
            throw new IllegalArgumentException("Symbol is not a valid "
                    + (isUs ? "US" : "NSE") + " symbol that can be priced");
        }
        return symbol;
    }

    private static ResponseStatusException alreadyHeld(String symbol) {
        return new ResponseStatusException(HttpStatus.CONFLICT, symbol + " is already in your holdings");
    }

    private static StockHoldingResponse toResponse(StockHoldingEntity holding) {
        return new StockHoldingResponse(holding.getId(), holding.getUserId(), holding.getCountry(),
                holding.getCurrency(), holding.getSymbol(), holding.getExchange(), holding.getName(),
                holding.getAssetType(), holding.getQuantity(), holding.getAverageCost());
    }
}
