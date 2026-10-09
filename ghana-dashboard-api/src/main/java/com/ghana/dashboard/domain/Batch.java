package com.ghana.dashboard.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "batches")
@Getter
@Setter
public class Batch {

    public enum Status {
        PRESSING, SETTLING, READY, BOTTLED, SOLD_OUT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @ManyToOne
    @JoinColumn(name = "oil_type_id", nullable = false)
    private OilType oilType;

    @ManyToOne
    @JoinColumn(name = "seed_type_id", nullable = false)
    private SeedType seedType;

    @Column(name = "seed_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal seedKg;

    @Column(name = "seed_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal seedCost = BigDecimal.ZERO;

    @Column(name = "press_start", nullable = false)
    private LocalDateTime pressStart;

    @Column(name = "oil_litres", nullable = false, precision = 12, scale = 3)
    private BigDecimal oilLitres;

    @Column(name = "khali_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal khaliKg;

    @Column(name = "yield_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal yieldPct;

    @Column(name = "settling_start")
    private LocalDateTime settlingStart;

    @Column(name = "ready_date")
    private LocalDate readyDate;

    @Column(name = "mfg_date", nullable = false)
    private LocalDate mfgDate;

    @Column(name = "best_before_date", nullable = false)
    private LocalDate bestBeforeDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 1000)
    private String notes;

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

    public Status getStatusEnum() {
        return Status.valueOf(status);
    }
}
