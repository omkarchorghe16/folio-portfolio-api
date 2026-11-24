package com.foliopro.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_entries_usa")
public class UsaPortfolioEntryEntity extends PortfolioEntryEntity {
    public UsaPortfolioEntryEntity() {
        super(WatchlistMarket.US, "USD");
    }
}
