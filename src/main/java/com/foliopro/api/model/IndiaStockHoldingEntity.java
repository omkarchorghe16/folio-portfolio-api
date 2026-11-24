package com.foliopro.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_holdings_india")
public class IndiaStockHoldingEntity extends StockHoldingEntity {
    /** Only NSE holdings are created today; the symbol search covers NSE only. */
    private static final String NSE = "NSE";

    @Column(nullable = false, length = 10)
    private String exchange = NSE;

    public IndiaStockHoldingEntity() {
        super(WatchlistMarket.INDIA, "INR");
    }

    @Override
    public String getExchange() {
        return exchange;
    }
}
