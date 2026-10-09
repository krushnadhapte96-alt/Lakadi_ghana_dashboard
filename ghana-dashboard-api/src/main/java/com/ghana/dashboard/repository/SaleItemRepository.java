package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySaleOrderByCreatedAtAsc(Sale sale);

    List<SaleItem> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<SaleItem> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<SaleItem> findBySale(Sale sale);
}
