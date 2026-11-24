package com.foliopro.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_sub_asset_types_india")
public class IndiaPortfolioSubAssetTypeEntity extends PortfolioSubAssetTypeEntity {
    public IndiaPortfolioSubAssetTypeEntity() {
        super(WatchlistMarket.INDIA);
    }
}
