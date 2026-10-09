package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.CashBookDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashBookDayRepository extends JpaRepository<CashBookDay, Long> {

    Optional<CashBookDay> findByShopIdAndDayDate(Long shopId, LocalDate date);

    List<CashBookDay> findByShopIdOrderByDayDateDesc(Long shopId);

    List<CashBookDay> findByShopIdAndDayDateBetweenOrderByDayDateDesc(
            Long shopId, LocalDate startDate, LocalDate endDate);

    List<CashBookDay> findByShopIdAndStatusOrderByDayDateDesc(
            Long shopId, CashBookDay.Status status);

    boolean existsByShopIdAndDayDate(Long shopId, LocalDate date);
}
