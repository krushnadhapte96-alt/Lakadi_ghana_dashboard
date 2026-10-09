package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderOrderByCreatedAtAsc(Order order);

    List<OrderStatusHistory> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtDesc(Long orderId);
}
