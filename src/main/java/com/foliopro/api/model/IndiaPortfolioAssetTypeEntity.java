package com.foliopro.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_asset_types_india")
public class IndiaPortfolioAssetTypeEntity extends PortfolioAssetTypeEntity {
    public IndiaPortfolioAssetTypeEntity() {
        super(WatchlistMarket.INDIA);
    }
}
