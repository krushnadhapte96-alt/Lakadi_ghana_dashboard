package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndShopId(Long id, Long shopId);

    @Query("""
            select e from Expense e
            where e.shopId = :shopId
              and e.expenseDate between :from and :to
              and (:categoryId is null or e.categoryId = :categoryId)
              and (:paymentMode is null or e.paymentMode = :paymentMode)
            order by e.expenseDate desc, e.createdAt desc
            """)
    List<Expense> search(@Param("shopId") Long shopId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("categoryId") Long categoryId,
                         @Param("paymentMode") String paymentMode);

    List<Expense> findByShopIdAndExpenseDateBetween(Long shopId, LocalDate from, LocalDate to);
}
