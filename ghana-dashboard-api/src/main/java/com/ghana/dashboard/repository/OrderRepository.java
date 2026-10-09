package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<Order> findByShopIdAndActiveTrueOrderByCreatedAtDesc(Long shopId);

    Optional<Order> findByShopIdAndBillNo(Long shopId, String billNo);

    List<Order> findByShopIdAndStatusOrderByCreatedAtDesc(Long shopId, Order.OrderStatus status);

    Page<Order> findByShopIdOrderByCreatedAtDesc(Long shopId, Pageable pageable);

    List<Order> findByShopIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long shopId,
            LocalDateTime start, LocalDateTime end);

    List<Order> findByShopIdAndExpectedDeliveryDateOrderByCreatedAtDesc(Long shopId, LocalDate date);

    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    boolean existsByShopIdAndBillNo(Long shopId, String billNo);

    long countByShopIdAndStatus(Long shopId, Order.OrderStatus status);

    long countByShopIdAndCreatedAtBetween(Long shopId, LocalDateTime start, LocalDateTime end);

    /** Delivered orders whose delivery date (falling back to creation date) is in [from, to]. */
    @Query("""
            select distinct o from Order o
            left join fetch o.items i
            left join fetch i.product p
            left join fetch p.oilType
            where o.shopId = :shopId and o.status = com.ghana.dashboard.domain.Order.OrderStatus.DELIVERED
              and o.active = true
              and ((o.actualDeliveryDate is not null and o.actualDeliveryDate between :from and :to)
                or (o.actualDeliveryDate is null and o.createdAt >= :start and o.createdAt < :end))
            """)
    List<Order> findDeliveredForPnl(@Param("shopId") Long shopId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);
}
