package com.foliopro.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_holdings_usa")
public class UsaStockHoldingEntity extends StockHoldingEntity {
    public UsaStockHoldingEntity() {
        super(WatchlistMarket.US, "USD");
    }

    @Override
    public String getExchange() {
        return null;
    }
}
