package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Customer;
import com.ghana.dashboard.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerAndActiveTrue(Customer customer);

    List<CustomerAddress> findByCustomerOrderByIsDefaultDesc(Customer customer);

    List<CustomerAddress> findByShopIdAndActiveTrueOrderByIsDefaultDesc(Long shopId);

    Optional<CustomerAddress> findByCustomerAndIsDefaultTrue(Customer customer);

    List<CustomerAddress> findByCustomerAndShopIdOrderByIsDefaultDesc(Customer customer, Long shopId);
}
