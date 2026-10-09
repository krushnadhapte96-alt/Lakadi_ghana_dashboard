package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<Sale> findByShopIdAndActiveTrueOrderByCreatedAtDesc(Long shopId);

    Optional<Sale> findByShopIdAndBillNo(Long shopId, String billNo);

    List<Sale> findByShopIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long shopId, 
            LocalDateTime start, LocalDateTime end);

    List<Sale> findByShopIdAndPaymentStatusOrderByCreatedAtDesc(Long shopId, 
            Sale.PaymentStatus paymentStatus);

    List<Sale> findByShopIdAndPaymentStatusIn(Long shopId, List<Sale.PaymentStatus> statuses);

    Page<Sale> findByShopIdOrderByCreatedAtDesc(Long shopId, Pageable pageable);

    List<Sale> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    boolean existsByShopIdAndBillNo(Long shopId, String billNo);

    long countByShopIdAndCreatedAtBetween(Long shopId, LocalDateTime start, LocalDateTime end);

    List<Sale> findByShopIdAndCreatedAtBetween(Long shopId, LocalDateTime start, LocalDateTime end);

    @Query("""
            select distinct s from Sale s
            left join fetch s.items i
            left join fetch i.product p
            left join fetch p.oilType
            where s.shopId = :shopId and s.isVoid = false and s.active = true
              and s.createdAt >= :start and s.createdAt < :end
            """)
    List<Sale> findForPnl(@Param("shopId") Long shopId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);
}
