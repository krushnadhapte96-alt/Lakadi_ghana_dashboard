package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.BatchPriceOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchPriceOverrideRepository extends JpaRepository<BatchPriceOverride, Long> {

    Optional<BatchPriceOverride> findByBatchStockId(Long batchStockId);

    List<BatchPriceOverride> findByShopId(Long shopId);

    void deleteByBatchStockId(Long batchStockId);
}
