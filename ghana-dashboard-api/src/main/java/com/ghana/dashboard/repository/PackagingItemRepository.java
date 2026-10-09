package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.PackagingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackagingItemRepository extends JpaRepository<PackagingItem, Long> {

    List<PackagingItem> findByShopIdAndActiveTrueOrderByNameAsc(Long shopId);

    List<PackagingItem> findByShopIdOrderByNameAsc(Long shopId);

    Optional<PackagingItem> findByShopIdAndNameAndType(Long shopId, String name, PackagingItem.PackagingType type);

    boolean existsByShopIdAndNameAndType(Long shopId, String name, PackagingItem.PackagingType type);
}
