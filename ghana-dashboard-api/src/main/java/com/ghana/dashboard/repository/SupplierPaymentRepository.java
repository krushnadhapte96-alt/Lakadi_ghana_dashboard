package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findByShopIdAndSupplierIdOrderByPaymentDateAsc(Long shopId, Long supplierId);

    List<SupplierPayment> findByShopIdAndSeedPurchaseId(Long shopId, Long seedPurchaseId);

    Optional<SupplierPayment> findByShopIdAndIdAndSupplierId(Long shopId, Long id, Long supplierId);
}
