package com.ghana.dashboard.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "expense_categories")
@Getter
@Setter
public class ExpenseCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "name_hi", nullable = false, length = 100)
    private String nameHi;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, unique = true)
    private CategoryType categoryType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String colour;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CategoryType {
        ELECTRICITY,
        LABOUR_SALARY,
        RENT,
        TRANSPORT,
        PACKAGING,
        REPAIRS,
        LAB_TESTING,
        LICENCE_FEES,
        OTHER
    }
}
