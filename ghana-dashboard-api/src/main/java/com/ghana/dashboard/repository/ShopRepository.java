package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, Long> {
}