package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.CostDefaults;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CostDefaultsRepository extends JpaRepository<CostDefaults, Long> {

    Optional<CostDefaults> findByShopId(Long shopId);
}
