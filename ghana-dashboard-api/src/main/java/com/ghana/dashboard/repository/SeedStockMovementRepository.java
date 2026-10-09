package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.SeedStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeedStockMovementRepository extends JpaRepository<SeedStockMovement, Long> {

    List<SeedStockMovement> findByShopIdAndSeedTypeIdOrderByCreatedAtAsc(Long shopId, Long seedTypeId);

    List<SeedStockMovement> findByShopIdAndSeedTypeIdOrderByCreatedAtDesc(Long shopId, Long seedTypeId);

    List<SeedStockMovement> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    SeedStockMovement findTopByShopIdAndSeedTypeIdOrderByCreatedAtDesc(Long shopId, Long seedTypeId);
}
