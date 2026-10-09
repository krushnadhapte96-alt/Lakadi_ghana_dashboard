package com.ghana.dashboard.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_packaging_usage")
@Getter
@Setter
public class BatchPackagingUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne
    @JoinColumn(name = "packaging_item_id", nullable = false)
    private PackagingItem packagingItem;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "units_used", nullable = false, precision = 12, scale = 3)
    private BigDecimal unitsUsed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
