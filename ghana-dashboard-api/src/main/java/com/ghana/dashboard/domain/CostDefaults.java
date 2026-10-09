package com.ghana.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cost_defaults")
@Getter
@Setter
public class CostDefaults {

    public enum OverheadMode {
        PER_BATCH, PER_KG
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "electricity_mode", nullable = false, length = 10)
    private OverheadMode electricityMode = OverheadMode.PER_BATCH;

    @Column(name = "electricity_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal electricityValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "labour_mode", nullable = false, length = 10)
    private OverheadMode labourMode = OverheadMode.PER_BATCH;

    @Column(name = "labour_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal labourValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "other_mode", nullable = false, length = 10)
    private OverheadMode otherMode = OverheadMode.PER_BATCH;

    @Column(name = "other_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherValue = BigDecimal.ZERO;

    @Column(name = "khali_value_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal khaliValuePerKg = BigDecimal.ZERO;

    @Column(name = "target_margin_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetMarginPct = BigDecimal.valueOf(30);

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
