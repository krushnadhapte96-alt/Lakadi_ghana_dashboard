package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.BatchCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchCostRepository extends JpaRepository<BatchCost, Long> {

    Optional<BatchCost> findByBatchId(Long batchId);

    List<BatchCost> findByShopIdAndBatch_OilType_IdOrderByComputedAtDesc(Long shopId, Long oilTypeId);
}
