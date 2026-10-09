package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.SeedType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeedTypeRepository extends JpaRepository<SeedType, Long> {

    List<SeedType> findByActiveTrueOrderByNameEnAsc();
}
