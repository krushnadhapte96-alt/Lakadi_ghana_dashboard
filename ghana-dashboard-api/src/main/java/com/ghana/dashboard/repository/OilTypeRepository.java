package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.OilType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OilTypeRepository extends JpaRepository<OilType, Long> {

    List<OilType> findByActiveTrueOrderByNameEnAsc();

    /** Used by Settings > Oil Types, which must also list deactivated rows. */
    List<OilType> findAllByOrderByNameEnAsc();

    Optional<OilType> findByNameEn(String nameEn);

    boolean existsByNameEn(String nameEn);
}
