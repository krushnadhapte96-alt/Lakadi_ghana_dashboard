package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.PackagingItem;
import com.ghana.dashboard.domain.PackagingStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackagingStockMovementRepository extends JpaRepository<PackagingStockMovement, Long> {

    List<PackagingStockMovement> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<PackagingStockMovement> findByShopIdAndItemOrderByCreatedAtDesc(Long shopId, PackagingItem item);

    List<PackagingStockMovement> findByItemIdOrderByCreatedAtDesc(Long itemId);
}
