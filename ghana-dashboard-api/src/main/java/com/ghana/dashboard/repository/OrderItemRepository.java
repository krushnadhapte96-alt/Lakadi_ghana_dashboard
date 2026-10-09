package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderOrderByCreatedAtAsc(Order order);

    List<OrderItem> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<OrderItem> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<OrderItem> findByOrder(Order order);
}
