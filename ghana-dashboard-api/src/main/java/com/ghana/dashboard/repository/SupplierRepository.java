package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByShopIdOrderByNameAsc(Long shopId);

    List<Supplier> findByShopIdAndActiveTrueOrderByNameAsc(Long shopId);

    Optional<Supplier> findByIdAndShopId(Long id, Long shopId);
}
