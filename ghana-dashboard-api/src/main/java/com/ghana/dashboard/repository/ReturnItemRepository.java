package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Return;
import com.ghana.dashboard.domain.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    List<ReturnItem> findByReturnRequestOrderByIdAsc(Return returnRequest);

    List<ReturnItem> findByShopId(Long shopId);

    List<ReturnItem> findByReturnRequestIn(Collection<Return> returns);

    /** Qty already claimed against a source's product by returns that were not rejected. */
    @Query("""
            select coalesce(sum(i.qty), 0) from ReturnItem i
            where i.returnRequest.shopId = :shopId
              and i.returnRequest.sourceType = :sourceType and i.returnRequest.sourceId = :sourceId
              and i.product.id = :productId
              and (i.returnRequest.resolution is null
                   or i.returnRequest.resolution <> com.ghana.dashboard.domain.Return.Resolution.REJECT)
            """)
    BigDecimal sumClaimedQty(@Param("shopId") Long shopId,
                             @Param("sourceType") Return.SourceType sourceType,
                             @Param("sourceId") Long sourceId,
                             @Param("productId") Long productId);
}
