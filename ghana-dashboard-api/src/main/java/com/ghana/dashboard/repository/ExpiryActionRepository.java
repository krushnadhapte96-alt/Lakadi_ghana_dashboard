package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.ExpiryAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpiryActionRepository extends JpaRepository<ExpiryAction, Long> {

    List<ExpiryAction> findByBatchStockIdOrderByCreatedAtDesc(Long batchStockId);

    List<ExpiryAction> findByShopIdAndActionTypeAndSnoozedUntilGreaterThanEqual(
            Long shopId, ExpiryAction.ActionType actionType, java.time.LocalDate today);
}
