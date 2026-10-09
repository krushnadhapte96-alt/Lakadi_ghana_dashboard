package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.SeedPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeedPurchaseRepository extends JpaRepository<SeedPurchase, Long> {

    Optional<SeedPurchase> findByIdAndShopId(Long id, Long shopId);

    List<SeedPurchase> findByShopIdAndSupplierIdOrderByPurchaseDateAsc(Long shopId, Long supplierId);

    List<SeedPurchase> findByShopIdAndSeedTypeIdOrderByPurchaseDateAsc(Long shopId, Long seedTypeId);

    @Query("""
            select p from SeedPurchase p
            where p.shopId = :shopId
              and p.purchaseDate between :from and :to
              and (:supplierId is null or p.supplier.id = :supplierId)
              and (:seedTypeId is null or p.seedType.id = :seedTypeId)
              and (:paymentStatus is null or p.paymentStatus = :paymentStatus)
            order by p.purchaseDate desc, p.createdAt desc
            """)
    List<SeedPurchase> search(@Param("shopId") Long shopId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("supplierId") Long supplierId,
                              @Param("seedTypeId") Long seedTypeId,
                              @Param("paymentStatus") SeedPurchase.PaymentStatus paymentStatus);
}
