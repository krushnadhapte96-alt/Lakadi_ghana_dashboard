package com.ghana.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_costs")
@Getter
@Setter
public class BatchCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "seed_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal seedCost = BigDecimal.ZERO;

    @Column(name = "electricity_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal electricityCost = BigDecimal.ZERO;

    @Column(name = "labour_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal labourCost = BigDecimal.ZERO;

    @Column(name = "other_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherCost = BigDecimal.ZERO;

    @Column(name = "khali_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal khaliCredit = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "cost_per_litre", nullable = false, precision = 12, scale = 4)
    private BigDecimal costPerLitre = BigDecimal.ZERO;

    @Column(name = "manual_override", nullable = false)
    private boolean manualOverride;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

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
