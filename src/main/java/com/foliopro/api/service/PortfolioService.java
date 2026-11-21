package com.foliopro.api.service;

import com.foliopro.api.dto.ApiDtos.*;
import com.foliopro.api.model.HoldingEntity;
import com.foliopro.api.model.PortfolioEntity;
import com.foliopro.api.repository.HoldingRepository;
import com.foliopro.api.repository.PortfolioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/** Portfolio CRUD, including each portfolio's holdings. */
@Service
public class PortfolioService {
    private static final String PORTFOLIO_NOT_FOUND = "Portfolio not found";

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;

    public PortfolioService(PortfolioRepository portfolioRepository, HoldingRepository holdingRepository) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PortfolioResponse> listPortfolios(int page, int size) {
        Page<PortfolioEntity> result = portfolioRepository.findAll(PageRequest.of(page, size, Sort.by("id").ascending()));
        return new PageResponse<>(result.map(this::toResponse).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(long id) {
        return toResponse(findPortfolio(id));
    }

    @Transactional
    public PortfolioResponse createPortfolio(PortfolioRequest request) {
        return save(new PortfolioEntity(), request);
    }

    /** Replaces the portfolio's fields and all of its holdings. */
    @Transactional
    public PortfolioResponse replacePortfolio(long id, PortfolioRequest request) {
        PortfolioEntity portfolio = findPortfolio(id);
        holdingRepository.deleteByPortfolioId(id);
        return save(portfolio, request);
    }

    @Transactional
    public void deletePortfolio(long id) {
        if (!portfolioRepository.existsById(id)) {
            throw new NoSuchElementException(PORTFOLIO_NOT_FOUND);
        }
        portfolioRepository.deleteById(id);
    }

    private PortfolioEntity findPortfolio(long id) {
        return portfolioRepository.findById(id).orElseThrow(() -> new NoSuchElementException(PORTFOLIO_NOT_FOUND));
    }

    private PortfolioResponse save(PortfolioEntity portfolio, PortfolioRequest request) {
        portfolio.setName(request.name().trim());
        portfolio.setCountry(request.country().trim());
        portfolio.setCurrency(request.currency());
        portfolio.setAssetType(request.assetType());
        portfolio.touch();
        PortfolioEntity saved = portfolioRepository.save(portfolio);
        if (request.holdings() != null) {
            for (HoldingRequest holding : request.holdings()) {
                holdingRepository.save(toHoldingEntity(saved, holding));
            }
        }
        return toResponse(saved);
    }

    private HoldingEntity toHoldingEntity(PortfolioEntity portfolio, HoldingRequest request) {
        HoldingEntity holding = new HoldingEntity();
        holding.setPortfolio(portfolio);
        holding.setSymbol(request.symbol());
        holding.setInstrument(request.instrument());
        holding.setQuantity(request.quantity());
        holding.setAverageCost(request.averageCost());
        holding.setMarketValue(request.marketValue());
        holding.setCurrency(request.currency());
        return holding;
    }

    private PortfolioResponse toResponse(PortfolioEntity portfolio) {
        var holdings = holdingRepository.findByPortfolioIdOrderById(portfolio.getId()).stream()
                .map(h -> new HoldingResponse(h.getSymbol(), h.getInstrument(), h.getQuantity(),
                        h.getAverageCost(), h.getMarketValue(), h.getCurrency()))
                .toList();
        return new PortfolioResponse(portfolio.getId(), portfolio.getName(), portfolio.getCountry(),
                portfolio.getCurrency(), portfolio.getAssetType(), holdings, false);
    }
}
