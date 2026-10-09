package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByShopIdAndActiveTrueOrderByNameAsc(Long shopId);

    List<Customer> findByShopIdOrderByNameAsc(Long shopId);

    Optional<Customer> findByShopIdAndPhone(Long shopId, String phone);

    Optional<Customer> findByPhone(String phone);

    boolean existsByShopIdAndPhone(Long shopId, String phone);

    List<Customer> findByShopIdAndNameContainingIgnoreCase(Long shopId, String name);
}
