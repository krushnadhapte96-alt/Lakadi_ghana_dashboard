package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<Batch> findByShopIdAndStatus(Long shopId, String status);

    Optional<Batch> findByShopIdAndCode(Long shopId, String code);

    List<Batch> findByShopIdAndOilTypeId(Long shopId, Long oilTypeId);

    List<Batch> findByShopIdAndStatusIn(Long shopId, List<String> statuses);

    long countByShopIdAndCreatedAtBetween(Long shopId, LocalDateTime start, LocalDateTime end);
}
