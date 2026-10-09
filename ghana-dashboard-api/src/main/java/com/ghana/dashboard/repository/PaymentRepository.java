package com.ghana.dashboard.repository;

import com.ghana.dashboard.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByShopIdOrderByPaymentDateDescCreatedAtDesc(Long shopId);

    List<Payment> findByShopIdAndPaymentDateOrderByCreatedAtDesc(Long shopId, LocalDate date);

    List<Payment> findByShopIdAndPaymentDateBetweenOrderByPaymentDateDescCreatedAtDesc(
            Long shopId, LocalDate startDate, LocalDate endDate);

    List<Payment> findByShopIdAndSourceTypeAndSourceId(Long shopId, Payment.SourceType sourceType, Long sourceId);

    List<Payment> findByShopIdAndDirectionOrderByPaymentDateDescCreatedAtDesc(
            Long shopId, Payment.Direction direction);

    long countByShopIdAndPaymentDate(Long shopId, LocalDate date);

    List<Payment> findByShopIdAndPaymentDateAndDirection(
            Long shopId, LocalDate date, Payment.Direction direction);
}
