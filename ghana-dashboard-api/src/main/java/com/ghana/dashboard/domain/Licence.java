package com.ghana.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mirror of the {@code licences} table so Hibernate's {@code ddl-auto=validate}
 * can check it; reads and writes go through {@code LicenceService} over JDBC,
 * like the other V12+ modules.
 */
@Entity
@Table(name = "licences")
@Getter
@Setter
public class Licence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "licence_number", nullable = false, length = 50)
    private String licenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LicenceType type;

    @Column(length = 120)
    private String issuer;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "fssai_gstin", length = 100)
    private String fssaiGstin;

    @Column(name = "document_path", length = 500)
    private String documentPath;

    @Column(name = "document_name", length = 255)
    private String documentName;

    @Column(name = "document_type", length = 100)
    private String documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LicenceStatus status;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public enum LicenceType {
        FSSAI,
        GST,
        SHOP_ESTABLISHMENT,
        TRADE,
        WEIGHING_SCALE_STAMPING,
        FIRE_NOC,
        OTHER
    }
}