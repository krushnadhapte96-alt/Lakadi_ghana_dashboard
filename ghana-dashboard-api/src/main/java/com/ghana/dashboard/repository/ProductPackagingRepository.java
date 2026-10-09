package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.ProductPackaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPackagingRepository extends JpaRepository<ProductPackaging, Long> {

    List<ProductPackaging> findByShopIdAndProductId(Long shopId, Long productId);

    Optional<ProductPackaging> findByShopIdAndProductIdAndPackagingItemId(Long shopId, Long productId, Long packagingItemId);

    List<ProductPackaging> findByShopIdAndPackagingItemId(Long shopId, Long packagingItemId);

    boolean existsByShopIdAndProductIdAndPackagingItemId(Long shopId, Long productId, Long packagingItemId);
}
