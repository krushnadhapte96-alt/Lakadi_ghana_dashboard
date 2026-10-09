package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByShopIdAndActiveTrueOrderByNameAsc(Long shopId);

    List<Product> findByShopIdAndIsKhaliTrueAndActiveTrueOrderByNameAsc(Long shopId);

    List<Product> findByShopIdAndIsKhaliFalseAndActiveTrueOrderByNameAsc(Long shopId);

    Optional<Product> findByShopIdAndSku(Long shopId, String sku);

    List<Product> findByShopIdOrderByNameAsc(Long shopId);

    boolean existsByShopIdAndSku(Long shopId, String sku);

    long countByShopId(Long shopId);
}
