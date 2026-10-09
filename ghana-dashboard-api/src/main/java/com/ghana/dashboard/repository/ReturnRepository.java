package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Return;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRepository extends JpaRepository<Return, Long> {

    List<Return> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<Return> findByShopIdAndStatusOrderByCreatedAtDesc(Long shopId, Return.Status status);

    Optional<Return> findByShopIdAndId(Long shopId, Long id);

    List<Return> findByShopIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long shopId, LocalDateTime start, LocalDateTime end);

    long countByShopIdAndCreatedAtBetween(Long shopId, LocalDateTime start, LocalDateTime end);
}
