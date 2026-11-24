package com.foliopro.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One user's invested amount and current value of one asset type in one month (entryMonth is the 1st). */
@MappedSuperclass
public abstract class PortfolioEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String country;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "entry_month", nullable = false)
    private LocalDate entryMonth;

    /** Code of a default type ({@link PortfolioAssetType}) or of one of the user's own types. */
    @Column(name = "asset_type", nullable = false, length = 40)
    private String assetType;

    /** One of the asset type's sub asset types, or null when the entry is for the asset type itself. */
    @Column(name = "sub_asset_type_id")
    private Long subAssetTypeId;

    @Column(name = "invested_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal investedAmount;

    @Column(name = "current_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected PortfolioEntryEntity(String country, String currency) {
        this.country = country;
        this.currency = currency;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCountry() { return country; }
    public String getCurrency() { return currency; }
    public LocalDate getEntryMonth() { return entryMonth; }
    public void setEntryMonth(LocalDate entryMonth) { this.entryMonth = entryMonth; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public Long getSubAssetTypeId() { return subAssetTypeId; }
    public void setSubAssetTypeId(Long subAssetTypeId) { this.subAssetTypeId = subAssetTypeId; }
    public BigDecimal getInvestedAmount() { return investedAmount; }
    public BigDecimal getCurrentValue() { return currentValue; }

    public void setAmounts(BigDecimal investedAmount, BigDecimal currentValue) {
        this.investedAmount = investedAmount;
        this.currentValue = currentValue;
        this.updatedAt = LocalDateTime.now();
    }
}
