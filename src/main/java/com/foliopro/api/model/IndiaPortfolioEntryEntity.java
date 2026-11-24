package com.foliopro.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_entries_india")
public class IndiaPortfolioEntryEntity extends PortfolioEntryEntity {
    public IndiaPortfolioEntryEntity() {
        super(WatchlistMarket.INDIA, "INR");
    }
}
