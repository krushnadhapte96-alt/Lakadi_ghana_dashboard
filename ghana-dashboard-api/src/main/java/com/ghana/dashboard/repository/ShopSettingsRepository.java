package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.ShopSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Long> {

    ShopSettings findByShopId(Long shopId);
}