package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.domain.SeedPurchase;
import com.ghana.dashboard.domain.Supplier;
import com.ghana.dashboard.domain.SupplierPayment;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.BeejDtos;
import com.ghana.dashboard.repository.PaymentRepository;
import com.ghana.dashboard.repository.SeedPurchaseRepository;
import com.ghana.dashboard.repository.SupplierPaymentRepository;
import com.ghana.dashboard.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SeedPurchaseRepository seedPurchaseRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final CashBookDayService cashBookDayService;
    private final UserService userService;

    public SupplierService(SupplierRepository supplierRepository,
                           SeedPurchaseRepository seedPurchaseRepository,
                           SupplierPaymentRepository supplierPaymentRepository,
                           PaymentRepository paymentRepository,
                           CashBookDayService cashBookDayService,
                           UserService userService) {
        this.supplierRepository = supplierRepository;
        this.seedPurchaseRepository = seedPurchaseRepository;
        this.supplierPaymentRepository = supplierPaymentRepository;
        this.paymentRepository = paymentRepository;
        this.cashBookDayService = cashBookDayService;
        this.userService = userService;
    }

    // ==================== CRUD ====================

    @Transactional(readOnly = true)
    public List<BeejDtos.SupplierResponse> getAllSuppliers(Long shopId) {
        return supplierRepository.findByShopIdOrderByNameAsc(shopId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BeejDtos.SupplierResponse getSupplier(Long shopId, Long id) {
        return toResponse(requireSupplier(shopId, id));
    }

    @Transactional
    public BeejDtos.SupplierResponse createSupplier(Long shopId, BeejDtos.SupplierRequest request) {
        Supplier supplier = new Supplier();
        supplier.setShopId(shopId);
        apply(supplier, request);
        supplier.setActive(true);
        supplierRepository.save(supplier);
        return toResponse(supplier);
    }

    @Transactional
    public BeejDtos.SupplierResponse updateSupplier(Long shopId, Long id, BeejDtos.SupplierRequest request) {
        Supplier supplier = requireSupplier(shopId, id);
        apply(supplier, request);
        supplierRepository.save(supplier);
        return toResponse(supplier);
    }

    @Transactional
    public void deleteSupplier(Long shopId, Long id) {
        Supplier supplier = requireSupplier(shopId, id);
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    // ==================== Payments ====================

    @Transactional
    public BeejDtos.SupplierPaymentResponse recordPayment(Long shopId, Long supplierId,
            BeejDtos.SupplierPaymentRequest request, Long userId) {
        Supplier supplier = requireSupplier(shopId, supplierId);
        SupplierPayment payment = createPayment(shopId, supplier, null, request.amount(), request.mode(),
                request.paymentDate(), request.referenceNo(), request.note(), userId);
        return toPaymentResponse(payment);
    }

    @Transactional
    public SupplierPayment createAutoPayment(Long shopId, Supplier supplier, Long seedPurchaseId,
            BigDecimal amount, Payment.PaymentMode mode, LocalDate paymentDate, Long userId) {
        return createPayment(shopId, supplier, seedPurchaseId, amount, mode, paymentDate, null,
                "Paid at purchase", userId);
    }

    @Transactional
    public void removePaymentsForPurchase(Long shopId, Long seedPurchaseId) {
        for (SupplierPayment payment : supplierPaymentRepository.findByShopIdAndSeedPurchaseId(shopId, seedPurchaseId)) {
            removeLedgerPayment(shopId, Payment.SourceType.SUPPLIER, payment);
            supplierPaymentRepository.delete(payment);
        }
    }

    private SupplierPayment createPayment(Long shopId, Supplier supplier, Long seedPurchaseId, BigDecimal amount,
            Payment.PaymentMode mode, LocalDate paymentDate, String referenceNo, String note, Long userId) {
        SupplierPayment payment = new SupplierPayment();
        payment.setShopId(shopId);
        payment.setSupplierId(supplier.getId());
        payment.setSeedPurchaseId(seedPurchaseId);
        payment.setAmount(amount);
        payment.setMode(mode);
        payment.setPaymentDate(paymentDate);
        payment.setReferenceNo(referenceNo);
        payment.setNote(note);
        payment.setCreatedBy(userId);
        SupplierPayment saved = supplierPaymentRepository.save(payment);

        Payment ledgerPayment = new Payment();
        ledgerPayment.setShopId(shopId);
        ledgerPayment.setDirection(Payment.Direction.OUT);
        ledgerPayment.setMode(mode);
        ledgerPayment.setAmount(amount);
        ledgerPayment.setSourceType(Payment.SourceType.SUPPLIER);
        ledgerPayment.setSourceId(supplier.getId());
        ledgerPayment.setReferenceNo(referenceNo);
        ledgerPayment.setPaymentDate(paymentDate);
        ledgerPayment.setNote("Supplier payment: " + supplier.getName() + (note != null ? " - " + note : ""));
        ledgerPayment.setCreatedBy(userId);
        Payment savedLedgerPayment = paymentRepository.save(ledgerPayment);
        cashBookDayService.recordPaymentForDay(shopId, savedLedgerPayment);

        return saved;
    }

    private void removeLedgerPayment(Long shopId, Payment.SourceType sourceType, SupplierPayment payment) {
        // Ledger payments created here are matched by source, amount, date and note; simplest is to look up by
        // supplier + date since supplier payments always create exactly one matching ledger row.
        paymentRepository.findByShopIdAndSourceTypeAndSourceId(shopId, sourceType, payment.getSupplierId()).stream()
                .filter(p -> p.getAmount().compareTo(payment.getAmount()) == 0
                        && p.getPaymentDate().equals(payment.getPaymentDate()))
                .findFirst()
                .ifPresent(ledgerPayment -> {
                    cashBookDayService.removePaymentFromDay(shopId, ledgerPayment);
                    paymentRepository.delete(ledgerPayment);
                });
    }

    // ==================== Ledger ====================

    @Transactional(readOnly = true)
    public BeejDtos.SupplierLedgerResponse getLedger(Long shopId, Long supplierId) {
        Supplier supplier = requireSupplier(shopId, supplierId);
        List<SeedPurchase> purchases = seedPurchaseRepository.findByShopIdAndSupplierIdOrderByPurchaseDateAsc(shopId, supplierId);
        List<SupplierPayment> payments = supplierPaymentRepository.findByShopIdAndSupplierIdOrderByPaymentDateAsc(shopId, supplierId);

        List<BeejDtos.SupplierLedgerEntry> entries = new ArrayList<>();
        for (SeedPurchase purchase : purchases) {
            entries.add(new BeejDtos.SupplierLedgerEntry(
                    purchase.getPurchaseDate(), "PURCHASE",
                    purchase.getQtyKg() + " kg @ " + purchase.getRatePerKg() + "/kg",
                    purchase.getTotalAmount(), BigDecimal.ZERO, null));
        }
        for (SupplierPayment payment : payments) {
            entries.add(new BeejDtos.SupplierLedgerEntry(
                    payment.getPaymentDate(), "PAYMENT",
                    payment.getMode() + (payment.getNote() != null ? " - " + payment.getNote() : ""),
                    BigDecimal.ZERO, payment.getAmount(), null));
        }
        entries.sort(Comparator.comparing(BeejDtos.SupplierLedgerEntry::date));

        BigDecimal running = BigDecimal.ZERO;
        List<BeejDtos.SupplierLedgerEntry> withRunningBalance = new ArrayList<>();
        for (BeejDtos.SupplierLedgerEntry entry : entries) {
            running = running.add(entry.debit()).subtract(entry.credit());
            withRunningBalance.add(new BeejDtos.SupplierLedgerEntry(
                    entry.date(), entry.type(), entry.description(), entry.debit(), entry.credit(), running));
        }

        BigDecimal totalPurchases = purchases.stream().map(SeedPurchase::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = payments.stream().map(SupplierPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BeejDtos.SupplierLedgerResponse(
                supplier.getId(), supplier.getName(), totalPurchases, totalPaid,
                totalPurchases.subtract(totalPaid), withRunningBalance);
    }

    // ==================== Helpers ====================

    private Supplier requireSupplier(Long shopId, Long id) {
        return supplierRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new DomainException(404, "Supplier not found"));
    }

    private void apply(Supplier supplier, BeejDtos.SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setAddress(blankToNull(request.address()));
        supplier.setGstin(blankToNull(request.gstin()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BeejDtos.SupplierResponse toResponse(Supplier supplier) {
        BigDecimal totalPurchases = seedPurchaseRepository
                .findByShopIdAndSupplierIdOrderByPurchaseDateAsc(supplier.getShopId(), supplier.getId()).stream()
                .map(SeedPurchase::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = supplierPaymentRepository
                .findByShopIdAndSupplierIdOrderByPaymentDateAsc(supplier.getShopId(), supplier.getId()).stream()
                .map(SupplierPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BeejDtos.SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getGstin(),
                supplier.isActive(),
                totalPurchases,
                totalPaid,
                totalPurchases.subtract(totalPaid),
                supplier.getCreatedAt() != null ? supplier.getCreatedAt().toString() : null
        );
    }

    private BeejDtos.SupplierPaymentResponse toPaymentResponse(SupplierPayment payment) {
        User createdBy = userService.findById(payment.getCreatedBy()).orElse(null);
        return new BeejDtos.SupplierPaymentResponse(
                payment.getId(),
                payment.getSupplierId(),
                payment.getSeedPurchaseId(),
                payment.getAmount(),
                payment.getMode().name(),
                payment.getPaymentDate(),
                payment.getReferenceNo(),
                payment.getNote(),
                createdBy != null ? createdBy.getName() : "",
                payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : ""
        );
    }
}
