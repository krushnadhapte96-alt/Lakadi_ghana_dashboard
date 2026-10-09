package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.SeedStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeedStockRepository extends JpaRepository<SeedStock, Long> {

    Optional<SeedStock> findByShopIdAndSeedTypeId(Long shopId, Long seedTypeId);

    List<SeedStock> findByShopId(Long shopId);
}
