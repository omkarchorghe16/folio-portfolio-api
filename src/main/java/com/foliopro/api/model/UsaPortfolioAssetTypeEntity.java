package com.foliopro.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_asset_types_usa")
public class UsaPortfolioAssetTypeEntity extends PortfolioAssetTypeEntity {
    public UsaPortfolioAssetTypeEntity() {
        super(WatchlistMarket.US);
    }
}
