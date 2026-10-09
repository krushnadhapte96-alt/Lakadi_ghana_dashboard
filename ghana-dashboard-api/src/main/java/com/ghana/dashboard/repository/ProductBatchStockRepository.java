package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.ProductBatchStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductBatchStockRepository extends JpaRepository<ProductBatchStock, Long> {

    List<ProductBatchStock> findByShopIdAndProductIdOrderByReceivedDateAsc(Long shopId, Long productId);

    List<ProductBatchStock> findByShopIdAndActiveTrueOrderByProductIdAsc(Long shopId);

    Optional<ProductBatchStock> findByShopIdAndProductIdAndBatchId(Long shopId, Long productId, String batchId);

    List<ProductBatchStock> findByShopIdAndProductId(Long shopId, Long productId);

    @Query("SELECT p FROM ProductBatchStock p WHERE p.shopId = :shopId AND p.product.id = :productId AND p.qtyAvailable > 0 ORDER BY p.receivedDate ASC")
    List<ProductBatchStock> findAvailableBatchesFIFO(Long shopId, Long productId);

    @Query("SELECT p FROM ProductBatchStock p WHERE p.shopId = :shopId AND p.product.id = :productId AND p.qtyAvailable > 0 AND p.expiryDate IS NOT NULL ORDER BY p.expiryDate ASC")
    List<ProductBatchStock> findAvailableBatchesFEFO(Long shopId, Long productId);
}
