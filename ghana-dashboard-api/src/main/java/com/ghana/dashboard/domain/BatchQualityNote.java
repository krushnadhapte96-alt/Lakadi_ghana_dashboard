package com.ghana.dashboard.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "batch_quality_notes")
@Getter
@Setter
public class BatchQualityNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false, length = 1000)
    private String note;

    @Column(name = "checked_by", length = 100)
    private String checkedBy;

    @Column(name = "quality_level", length = 20)
    private String qualityLevel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
