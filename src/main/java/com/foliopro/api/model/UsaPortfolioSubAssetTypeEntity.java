package com.foliopro.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_sub_asset_types_usa")
public class UsaPortfolioSubAssetTypeEntity extends PortfolioSubAssetTypeEntity {
    public UsaPortfolioSubAssetTypeEntity() {
        super(WatchlistMarket.US);
    }
}
