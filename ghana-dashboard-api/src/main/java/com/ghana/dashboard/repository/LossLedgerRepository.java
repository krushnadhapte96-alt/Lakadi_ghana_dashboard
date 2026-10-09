package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.LossLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LossLedgerRepository extends JpaRepository<LossLedger, Long> {

    @Query("""
            select l from LossLedger l
            where l.shopId = :shopId
              and l.lossDate between :from and :to
              and (:lossType is null or l.lossType = :lossType)
              and (:productId is null or l.productId = :productId)
            order by l.lossDate desc, l.createdAt desc
            """)
    List<LossLedger> search(@Param("shopId") Long shopId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("lossType") LossLedger.LossType lossType,
                            @Param("productId") Long productId);

    List<LossLedger> findByShopIdAndLossDateBetween(Long shopId, LocalDate from, LocalDate to);
}
