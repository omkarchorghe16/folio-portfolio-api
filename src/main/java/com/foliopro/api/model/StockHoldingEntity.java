package com.foliopro.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One stock or ETF a user holds: total quantity (if known) and average cost per share. Prices are not stored here. */
@MappedSuperclass
public abstract class StockHoldingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String country;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "asset_type", nullable = false, length = 10)
    private StockHoldingAssetType assetType;

    /** Null when the user does not know it. */
    @Column(precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "average_cost", nullable = false, precision = 20, scale = 6)
    private BigDecimal averageCost;

    /** Sector name picked by the user, or null. */
    @Column(length = 100)
    private String sector;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected StockHoldingEntity(String country, String currency) {
        this.country = country;
        this.currency = currency;
    }

    /** Exchange the symbol trades on, or null when the symbol alone identifies it (US). */
    public abstract String getExchange();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCountry() { return country; }
    public String getCurrency() { return currency; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StockHoldingAssetType getAssetType() { return assetType; }
    public void setAssetType(StockHoldingAssetType assetType) { this.assetType = assetType; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAverageCost() { return averageCost; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public void setPosition(BigDecimal quantity, BigDecimal averageCost) {
        this.quantity = quantity;
        this.averageCost = averageCost;
        this.updatedAt = LocalDateTime.now();
    }
}
