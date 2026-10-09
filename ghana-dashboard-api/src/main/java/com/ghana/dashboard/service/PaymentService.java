package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.*;
import com.ghana.dashboard.dto.PaymentDtos;
import com.ghana.dashboard.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SaleRepository saleRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserService userService;
    private final CashBookDayService cashBookDayService;

    public PaymentService(PaymentRepository paymentRepository,
                         SaleRepository saleRepository,
                         OrderRepository orderRepository,
                         CustomerRepository customerRepository,
                         UserService userService,
                         CashBookDayService cashBookDayService) {
        this.paymentRepository = paymentRepository;
        this.saleRepository = saleRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.userService = userService;
        this.cashBookDayService = cashBookDayService;
    }

    // ==================== Auto Payment Creation ====================

    @Transactional
    public Payment createAutoPaymentForSale(Sale sale) {
        // Only create auto payment for non-UDHAAR sales
        if (Sale.PaymentMode.UDHAAR.equals(sale.getPaymentMode())) {
            return null;
        }

        Payment payment = new Payment();
        payment.setShopId(sale.getShopId());
        payment.setDirection(Payment.Direction.IN);
        payment.setMode(mapPaymentMode(sale.getPaymentMode()));
        payment.setAmount(sale.getPaidAmount() != null ? sale.getPaidAmount() : sale.getTotalAmount());
        payment.setSourceType(Payment.SourceType.SALE);
        payment.setSourceId(sale.getId());
        payment.setReferenceNo(sale.getBillNo());
        payment.setPaymentDate(sale.getCreatedAt().toLocalDate());
        payment.setNote("Auto payment for sale " + sale.getBillNo());
        payment.setCreatedBy(sale.getUser().getId());
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        // Update cash book day
        cashBookDayService.recordPaymentForDay(sale.getShopId(), savedPayment);

        return savedPayment;
    }

    @Transactional
    public Payment createAutoPaymentForOrder(Order order) {
        // Only create auto payment for COD/CASH/UPI orders on delivery
        if (!Order.PaymentMode.COD.equals(order.getPaymentMode()) &&
            !Order.PaymentMode.CASH.equals(order.getPaymentMode()) &&
            !Order.PaymentMode.UPI.equals(order.getPaymentMode())) {
            return null;
        }

        Payment payment = new Payment();
        payment.setShopId(order.getShopId());
        payment.setDirection(Payment.Direction.IN);
        payment.setMode(mapPaymentMode(order.getPaymentMode()));
        payment.setAmount(order.getPaidAmount() != null ? order.getPaidAmount() : order.getTotalAmount());
        payment.setSourceType(Payment.SourceType.ORDER);
        payment.setSourceId(order.getId());
        payment.setReferenceNo(order.getBillNo());
        payment.setPaymentDate(LocalDate.now());
        payment.setNote("Auto payment for order " + order.getBillNo());
        payment.setCreatedBy(order.getUser().getId());
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        // Update cash book day
        cashBookDayService.recordPaymentForDay(order.getShopId(), savedPayment);

        return savedPayment;
    }

    private Payment.PaymentMode mapPaymentMode(Sale.PaymentMode mode) {
        return switch (mode) {
            case CASH -> com.ghana.dashboard.domain.Payment.PaymentMode.CASH;
            case UPI -> com.ghana.dashboard.domain.Payment.PaymentMode.UPI;
            case CARD -> com.ghana.dashboard.domain.Payment.PaymentMode.CARD;
            case UDHAAR -> com.ghana.dashboard.domain.Payment.PaymentMode.CASH; // Default for udhaar when paid
        };
    }

    private Payment.PaymentMode mapPaymentMode(Order.PaymentMode mode) {
        return switch (mode) {
            case COD, CASH, UDHAAR -> com.ghana.dashboard.domain.Payment.PaymentMode.CASH;
            case UPI -> com.ghana.dashboard.domain.Payment.PaymentMode.UPI;
        };
    }

    // ==================== Manual Payment CRUD ====================

    @Transactional
    public PaymentDtos.PaymentResponse createPayment(Long shopId, 
            PaymentDtos.PaymentRequest request, Long userId) {
        // Check if day is closed
        cashBookDayService.verifyDayNotClosed(shopId, request.paymentDate(), userId);

        Payment payment = new Payment();
        payment.setShopId(shopId);
        payment.setDirection(request.direction());
        payment.setMode(request.mode());
        payment.setAmount(request.amount());
        payment.setSourceType(request.sourceType());
        payment.setSourceId(request.sourceId());
        payment.setReferenceNo(request.referenceNo());
        payment.setPaymentDate(request.paymentDate());
        payment.setNote(request.note());
        payment.setCreatedBy(userId);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        // Update cash book day
        cashBookDayService.recordPaymentForDay(shopId, savedPayment);

        // Update source entity payment status if applicable
        updateSourcePaymentStatus(shopId, savedPayment);

        return toPaymentResponse(savedPayment);
    }

    private void updateSourcePaymentStatus(Long shopId, Payment payment) {
        if (Payment.Direction.IN.equals(payment.getDirection()) && 
            Payment.SourceType.SALE.equals(payment.getSourceType())) {
            
            saleRepository.findById(payment.getSourceId())
                    .filter(s -> s.getShopId().equals(shopId))
                    .ifPresent(sale -> {
                        BigDecimal totalPaid = paymentRepository.findByShopIdAndSourceTypeAndSourceId(
                                        shopId, Payment.SourceType.SALE, sale.getId())
                                .stream()
                                .map(Payment::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        
                        if (totalPaid.compareTo(sale.getTotalAmount()) >= 0) {
                            sale.setPaymentStatus(Sale.PaymentStatus.PAID);
                            saleRepository.save(sale);
                        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                            sale.setPaymentStatus(Sale.PaymentStatus.PARTIAL);
                            saleRepository.save(sale);
                        }
                    });
        }
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentResponse getPayment(Long shopId, Long id) {
        Payment payment = paymentRepository.findById(id)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Payment not found"));
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentSummaryResponse> getPayments(
            Long shopId, PaymentDtos.PaymentFilter filter) {
        
        LocalDate dateFrom = filter.dateFrom() != null ? filter.dateFrom() : LocalDate.now().minusMonths(1);
        LocalDate dateTo = filter.dateTo() != null ? filter.dateTo() : LocalDate.now();

        List<Payment> payments;
        
        if (filter.direction() != null && filter.mode() != null) {
            payments = paymentRepository.findByShopIdAndPaymentDateBetweenOrderByPaymentDateDescCreatedAtDesc(
                    shopId, dateFrom, dateTo)
                    .stream()
                    .filter(p -> p.getDirection().equals(filter.direction()))
                    .filter(p -> p.getMode().equals(filter.mode()))
                    .collect(Collectors.toList());
        } else if (filter.direction() != null) {
            payments = paymentRepository.findByShopIdAndPaymentDateBetweenOrderByPaymentDateDescCreatedAtDesc(
                    shopId, dateFrom, dateTo)
                    .stream()
                    .filter(p -> p.getDirection().equals(filter.direction()))
                    .collect(Collectors.toList());
        } else if (filter.mode() != null) {
            payments = paymentRepository.findByShopIdAndPaymentDateBetweenOrderByPaymentDateDescCreatedAtDesc(
                    shopId, dateFrom, dateTo)
                    .stream()
                    .filter(p -> p.getMode().equals(filter.mode()))
                    .collect(Collectors.toList());
        } else {
            payments = paymentRepository.findByShopIdAndPaymentDateBetweenOrderByPaymentDateDescCreatedAtDesc(
                    shopId, dateFrom, dateTo);
        }

        return payments.stream()
                .map(this::toPaymentSummaryResponse)
                .collect(Collectors.toList());
    }

    // ==================== Pending Payments ====================

    @Transactional(readOnly = true)
    public List<PaymentDtos.PendingPaymentResponse> getPendingPayments(Long shopId) {
        LocalDate today = LocalDate.now();
        
        // Get pending sales (UDHAAR with UNPAID or PARTIAL)
        List<Sale> pendingSales = saleRepository.findByShopIdAndPaymentStatusIn(
                shopId, List.of(Sale.PaymentStatus.UNPAID, Sale.PaymentStatus.PARTIAL));
        
        return pendingSales.stream()
                .map(sale -> {
                    BigDecimal dueAmount = sale.getTotalAmount().subtract(sale.getPaidAmount());
                    int daysOverdue = sale.getCreatedAt().toLocalDate().until(today).getDays();
                    return new PaymentDtos.PendingPaymentResponse(
                            sale.getId(),
                            sale.getBillNo(),
                            sale.getCustomer() != null ? sale.getCustomer().getName() : "Walk-in",
                            sale.getCustomer() != null ? sale.getCustomer().getPhone() : "",
                            sale.getTotalAmount(),
                            sale.getPaidAmount(),
                            dueAmount,
                            Payment.SourceType.SALE,
                            sale.getCreatedAt().toLocalDate(),
                            daysOverdue,
                            sale.getPaymentMode().name(),
                            sale.getPaymentStatus().name()
                    );
                })
                .collect(Collectors.toList());
    }

    // ==================== CSV Export ====================

    @Transactional(readOnly = true)
    public String exportPaymentsCsv(Long shopId, LocalDate dateFrom, LocalDate dateTo) {
        List<Payment> payments = paymentRepository
                .findByShopIdAndPaymentDateBetweenOrderByPaymentDateDescCreatedAtDesc(
                        shopId, dateFrom, dateTo);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Date,Direction,Mode,Amount,Source Type,Source ID,Reference No,Note,Created By,Created At\n");
        
        for (Payment p : payments) {
            csv.append(p.getId()).append(",")
               .append(p.getPaymentDate()).append(",")
               .append(p.getDirection()).append(",")
               .append(p.getMode()).append(",")
               .append(p.getAmount()).append(",")
               .append(p.getSourceType()).append(",")
               .append(p.getSourceId() == null ? "" : p.getSourceId()).append(",")
               .append(escapeCsv(p.getReferenceNo())).append(",")
               .append(escapeCsv(p.getNote())).append(",")
               .append(p.getCreatedBy()).append(",")
               .append(p.getCreatedAt()).append("\n");
        }
        
        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace(",", ";").replace("\n", " ").replace("\r", " ");
    }

    // ==================== Helper Methods ====================

    private PaymentDtos.PaymentResponse toPaymentResponse(Payment payment) {
        User createdBy = userService.getById(payment.getCreatedBy());
        return new PaymentDtos.PaymentResponse(
                payment.getId(),
                payment.getShopId(),
                payment.getDirection(),
                payment.getMode(),
                payment.getAmount(),
                payment.getSourceType(),
                payment.getSourceId(),
                payment.getReferenceNo(),
                payment.getPaymentDate(),
                payment.getNote(),
                payment.getCreatedBy(),
                createdBy != null ? createdBy.getName() : "",
                payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : "",
                payment.getUpdatedAt() != null ? payment.getUpdatedAt().toString() : ""
        );
    }

    private PaymentDtos.PaymentSummaryResponse toPaymentSummaryResponse(Payment payment) {
        return new PaymentDtos.PaymentSummaryResponse(
                payment.getId(),
                payment.getDirection(),
                payment.getMode(),
                payment.getAmount(),
                payment.getSourceType(),
                payment.getSourceId(),
                payment.getReferenceNo(),
                payment.getPaymentDate(),
                payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : ""
        );
    }

    @Transactional(readOnly = true)
    public Payment findById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Payment not found"));
    }
}
