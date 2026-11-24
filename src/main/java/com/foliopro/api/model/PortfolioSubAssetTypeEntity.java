package com.foliopro.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

/** One of the many sub asset types of a user's asset type. The code and the asset type never change. */
@MappedSuperclass
public abstract class PortfolioSubAssetTypeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String country;

    /** Row of the same user in portfolio_asset_types_*. */
    @Column(name = "asset_type_id", nullable = false)
    private Long assetTypeId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** Not offered for new months; amounts of past months stay. */
    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected PortfolioSubAssetTypeEntity(String country) {
        this.country = country;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCountry() { return country; }
    public Long getAssetTypeId() { return assetTypeId; }
    public void setAssetTypeId(Long assetTypeId) { this.assetTypeId = assetTypeId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public boolean isHidden() { return hidden; }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
        this.updatedAt = LocalDateTime.now();
    }
}
