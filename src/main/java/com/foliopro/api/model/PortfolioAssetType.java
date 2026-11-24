package com.foliopro.api.model;

/**
 * The asset types every user starts with, in display order. Their names are stored as the code in
 * portfolio_entries_*; users may rename them and add their own types (portfolio_asset_types_*).
 */
public enum PortfolioAssetType {
    STOCKS("Stocks"),
    MUTUAL_FUNDS("Mutual funds"),
    ETFS("ETFs"),
    RESIDENTIAL_PROPERTY("Residential property"),
    COMMERCIAL_PROPERTY("Commercial property"),
    LAND("Land"),
    REITS("REITs"),
    BONDS_FIXED_INCOME("Bonds / fixed income"),
    CASH("Cash"),
    CRYPTO("Crypto"),
    GOLD_COMMODITIES("Gold / commodities");

    private static final String INDIA_CASH_NAME = "Cash / fixed deposits";

    private final String defaultName;

    PortfolioAssetType(String defaultName) {
        this.defaultName = defaultName;
    }

    /** Name shown until the user renames the type. */
    public String defaultName(String country) {
        return this == CASH && !WatchlistMarket.isUs(country) ? INDIA_CASH_NAME : defaultName;
    }
}
