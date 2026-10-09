package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<StockMovement> findByShopIdAndProductIdOrderByCreatedAtDesc(Long shopId, Long productId);

    @Query("SELECT s FROM StockMovement s WHERE s.shopId = :shopId AND s.type IN :types ORDER BY s.createdAt DESC")
    List<StockMovement> findByShopIdAndTypes(Long shopId, List<StockMovement.MovementType> types);

    List<StockMovement> findByShopIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long shopId, LocalDateTime start, LocalDateTime end);

    List<StockMovement> findByShopIdAndProductIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long shopId, Long productId, LocalDateTime start, LocalDateTime end);
}
