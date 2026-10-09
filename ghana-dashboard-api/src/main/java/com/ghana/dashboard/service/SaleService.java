package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.BatchPriceOverride;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.SalesDtos;
import com.ghana.dashboard.event.CustomerActivityEvent;
import com.ghana.dashboard.event.SaleOnCreditEvent;
import com.ghana.dashboard.event.SaleVoidedEvent;
import com.ghana.dashboard.repository.BatchPriceOverrideRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.SaleItemRepository;
import com.ghana.dashboard.repository.SaleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final BatchPriceOverrideRepository batchPriceOverrideRepository;
    private final KhataLedgerService khataLedgerService;
    private final SettingsService settingsService;
    private final DashboardCache dashboardCache;

    public SaleService(SaleRepository saleRepository,
                       SaleItemRepository saleItemRepository,
                       ProductRepository productRepository,
                       InventoryService inventoryService,
                       CustomerService customerService,
                       UserService userService,
                       PaymentService paymentService,
                       ApplicationEventPublisher eventPublisher,
                       BatchPriceOverrideRepository batchPriceOverrideRepository,
                       KhataLedgerService khataLedgerService,
                       SettingsService settingsService,
                       DashboardCache dashboardCache) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.userService = userService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.batchPriceOverrideRepository = batchPriceOverrideRepository;
        this.khataLedgerService = khataLedgerService;
        this.settingsService = settingsService;
        this.dashboardCache = dashboardCache;
    }

    // ==================== Bill Number Generation ====================

    private String generateBillNo(Long shopId) {
        // Owner-configured from Settings > Billing; falls back to "S" for shops that
        // have never opened the settings screen.
        String prefix = settingsService.text(shopId, "billing.bill_prefix", "S");
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        
        // Count sales for today
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(23, 59, 59);
        long count = saleRepository.countByShopIdAndCreatedAtBetween(shopId, todayStart, todayEnd) + 1;
        
        return prefix + "-" + datePart + "-" + String.format("%04d", count);
    }

    // ==================== Sale CRUD ====================

    @Transactional
    public SalesDtos.SaleResponse createSale(Long shopId, SalesDtos.SaleRequest request, Long userId) {
        User user = userService.getById(userId);
        
        // Get or create customer
        Sale sale = new Sale();
        if (request.customerId() != null) {
            sale.setCustomer(customerService.findById(request.customerId()));
        } else if (request.customerPhone() != null && !request.customerPhone().isBlank()) {
            customerService.getOrCreateCustomerByPhone(shopId, request.customerPhone());
            sale.setCustomer(customerService.findById(
                    customerService.getAllCustomers(shopId).stream()
                            .filter(c -> c.phone().equals(request.customerPhone()))
                            .findFirst()
                            .map(SalesDtos.CustomerResponse::id)
                            .orElse(null)));
        }

        sale.setShopId(shopId);
        sale.setUser(user);
        sale.setBillNo(generateBillNo(shopId));

        // Calculate subtotal and validate items
        BigDecimal subtotal = BigDecimal.ZERO;
        List<SaleItem> saleItems = new ArrayList<>();
        
        for (SalesDtos.SaleItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .filter(p -> p.getShopId().equals(shopId))
                    .orElseThrow(() -> new DomainException(400, "Product not found or does not belong to this shop"));

            if (!product.isActive()) {
                throw new DomainException(400, "Product " + product.getName() + " is inactive");
            }

            BigDecimal availableStock = inventoryService.getAvailableStockExcludingReserved(shopId, product.getId());
            if (availableStock.compareTo(itemRequest.qty()) < 0) {
                throw new DomainException(400, 
                        "Insufficient stock for product " + product.getName() + 
                        ". Available: " + availableStock + ", Requested: " + itemRequest.qty());
            }

            ProductBatchStock fifoBatch = inventoryService.getOldestAvailableBatch(shopId, product.getId());
            BigDecimal rate = itemRequest.rate();
            if (fifoBatch != null) {
                BigDecimal ceiling = batchPriceOverrideRepository.findByBatchStockId(fifoBatch.getId())
                        .map(BatchPriceOverride::getDiscountPct)
                        .map(pct -> product.getSellingPrice()
                                .multiply(BigDecimal.valueOf(100).subtract(pct))
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                        .orElse(null);
                if (ceiling != null) {
                    rate = rate.min(ceiling);
                }
            }

            BigDecimal amount = rate.multiply(itemRequest.qty()).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(amount);

            // Create sale item
            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setShopId(shopId);
            saleItem.setProduct(product);
            saleItem.setBatchId(fifoBatch != null ? fifoBatch.getBatchId() : null);
            saleItem.setSku(product.getSku());
            saleItem.setProductName(product.getName());
            saleItem.setQty(itemRequest.qty());
            saleItem.setUnit(product.getUnit().name());
            saleItem.setRate(rate);
            saleItem.setAmount(amount);
            saleItem.setCostPrice(product.getCostPrice());
            saleItem.setProfit(amount.subtract(product.getCostPrice().multiply(itemRequest.qty()))
                    .setScale(2, RoundingMode.HALF_UP));
            saleItem.setCreatedAt(LocalDateTime.now());
            
            saleItems.add(saleItem);
        }

        // Calculate discount and total
        BigDecimal discount = request.discount();
        BigDecimal totalAmount = subtotal;
        
        if (Sale.DiscountType.PERCENT.name().equals(request.discountType())) {
            discount = subtotal.multiply(discount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalAmount = subtotal.subtract(discount);
        } else {
            totalAmount = subtotal.subtract(discount);
        }

        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setDiscountType(Sale.DiscountType.valueOf(request.discountType()));
        sale.setTotalAmount(totalAmount);
        sale.setPaymentMode(Sale.PaymentMode.valueOf(request.paymentMode()));
        boolean udhaar = Sale.PaymentMode.UDHAAR.equals(sale.getPaymentMode());
        sale.setPaidAmount(request.paidAmount() != null ? request.paidAmount() : (udhaar ? BigDecimal.ZERO : totalAmount));
        
        // Set payment status
        if (Sale.PaymentMode.UDHAAR.equals(sale.getPaymentMode())) {
            sale.setPaymentStatus(Sale.PaymentStatus.UNPAID);
        } else if (sale.getPaidAmount().compareTo(totalAmount) >= 0) {
            sale.setPaymentStatus(Sale.PaymentStatus.PAID);
        } else if (sale.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            sale.setPaymentStatus(Sale.PaymentStatus.PARTIAL);
        } else {
            sale.setPaymentStatus(Sale.PaymentStatus.UNPAID);
        }

        sale.setPaymentReference(request.paymentReference());
        sale.setNotes(request.notes());
        sale.setActive(true);
        sale.setVoid(false);
        sale.setCreatedAt(LocalDateTime.now());
        sale.setUpdatedAt(LocalDateTime.now());

        if (udhaar) {
            if (sale.getCustomer() == null) {
                throw new DomainException(400, "Select a customer for an udhaar sale");
            }
            khataLedgerService.assertCreditAllowed(shopId, sale.getCustomer().getPhone(), totalAmount.subtract(sale.getPaidAmount()),
                    Boolean.TRUE.equals(request.overrideCreditLimit()));
        }

        saleRepository.save(sale);

        // Save sale items
        for (SaleItem saleItem : saleItems) {
            saleItem.setSale(sale);
            saleItemRepository.save(saleItem);
        }

        // Deduct stock FIFO
        for (SaleItem saleItem : saleItems) {
            Product product = saleItem.getProduct();
            // Snapshot the level before deducting: the listener needs the "before" figure to tell a
            // threshold crossing apart from a product that was already short.
            BigDecimal availableBefore = inventoryService.getAvailableStock(shopId, product.getId());
            inventoryService.deductStockFIFO(
                    product, saleItem.getQty(), userId, LocalDateTime.now(),
                    "Sale: " + sale.getBillNo(), "SALE", sale.getId());
            // Published after the deduction so the listener's "after" read sees the real new level.
            eventPublisher.publishEvent(new com.ghana.dashboard.event.StockLevelChangedEvent(
                    shopId, product.getId(), product.getName(), product.getSku(),
                    availableBefore, product.getLowStockThreshold()));
        }

        // Create auto payment for non-UDHAAR sales
        Payment autoPayment = paymentService.createAutoPaymentForSale(sale);

        // Publish UDHAAR event if payment mode is UDHAAR
        if (Sale.PaymentMode.UDHAAR.equals(sale.getPaymentMode())) {
            eventPublisher.publishEvent(new SaleOnCreditEvent(
                    this, sale.getShopId(), sale.getId(), sale.getBillNo(),
                    sale.getCustomer() != null ? sale.getCustomer().getId() : null,
                    sale.getCustomer() != null ? sale.getCustomer().getName() : null,
                    sale.getCustomer() != null ? sale.getCustomer().getPhone() : null,
                    sale.getTotalAmount(), "SALE", sale.getCreatedAt()));
        }

        if (sale.getCustomer() != null) {
            eventPublisher.publishEvent(new CustomerActivityEvent(shopId, sale.getCustomer().getId()));
        }

        dashboardCache.evict(shopId);
        return toSaleResponse(sale);
    }

    @Transactional(readOnly = true)
    public SalesDtos.SaleResponse getSale(Long shopId, Long id) {
        return saleRepository.findById(id)
                .filter(s -> s.getShopId().equals(shopId))
                .map(this::toSaleResponse)
                .orElseThrow(() -> new DomainException(404, "Sale not found"));
    }

    @Transactional(readOnly = true)
    public Page<SalesDtos.SaleSummaryResponse> getSales(Long shopId, SalesDtos.SaleFilter filter, Pageable pageable) {
        List<Sale> sales;
        
        if (filter.customerId() != null) {
            sales = saleRepository.findByCustomerIdOrderByCreatedAtDesc(filter.customerId());
        } else {
            sales = saleRepository.findByShopIdOrderByCreatedAtDesc(shopId);
        }

        List<SalesDtos.SaleSummaryResponse> responses = sales.stream()
                .filter(s -> {
                    if (filter.paymentMode() != null && !s.getPaymentMode().name().equals(filter.paymentMode())) {
                        return false;
                    }
                    if (filter.paymentStatus() != null && !s.getPaymentStatus().name().equals(filter.paymentStatus())) {
                        return false;
                    }
                    return true;
                })
                .map(this::toSaleSummaryResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, responses.size());
    }

    @Transactional
    public SalesDtos.SaleResponse voidSale(Long shopId, Long id, SalesDtos.SaleVoidRequest request, Long userId) {
        Sale sale = saleRepository.findById(id)
                .filter(s -> s.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Sale not found"));

        if (sale.isVoid()) {
            throw new DomainException(400, "Sale already voided");
        }

        // Check if same day (OWNER can override, but we'll check in controller)
        LocalDateTime now = LocalDateTime.now();
        if (sale.getCreatedAt().toLocalDate().isBefore(now.toLocalDate())) {
            throw new DomainException(400, "Sale can only be voided on the same day");
        }

        User voidedBy = userService.getById(userId);
        
        // Restock items (reverse the sale)
        List<SaleItem> items = saleItemRepository.findBySale(sale);
        for (SaleItem item : items) {
            Product product = item.getProduct();
            // Return stock to inventory
            inventoryService.returnStock(
                    product, item.getQty(), product.getCostPrice(),
                    "Void: " + sale.getBillNo(), userId, LocalDateTime.now());
        }

        // Update sale
        sale.setVoid(true);
        sale.setVoidReason(request.reason());
        sale.setVoidedBy(voidedBy);
        sale.setVoidedAt(now);
        sale.setActive(false);
        sale.setUpdatedAt(now);

        saleRepository.save(sale);
        eventPublisher.publishEvent(new SaleVoidedEvent(shopId, sale.getId(), "SALE"));
        if (sale.getCustomer() != null) {
            eventPublisher.publishEvent(new CustomerActivityEvent(shopId, sale.getCustomer().getId()));
        }

        dashboardCache.evict(shopId);
        return toSaleResponse(sale);
    }

    @Transactional
    public SalesDtos.SaleResponse updatePaymentStatus(Long shopId, Long saleId, 
            SalesDtos.UpdatePaymentStatusRequest request, Long userId) {
        Sale sale = saleRepository.findById(saleId)
                .filter(s -> s.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Sale not found"));

        sale.setPaymentStatus(Sale.PaymentStatus.valueOf(request.paymentStatus()));
        if (request.paidAmount() != null) {
            sale.setPaidAmount(request.paidAmount());
        }
        if (request.paymentReference() != null) {
            sale.setPaymentReference(request.paymentReference());
        }
        sale.setUpdatedAt(LocalDateTime.now());

        saleRepository.save(sale);
        dashboardCache.evict(shopId);
        return toSaleResponse(sale);
    }

    // ==================== Helper Methods ====================

    private SalesDtos.SaleResponse toSaleResponse(Sale sale) {
        List<SalesDtos.SaleItemResponse> itemResponses = saleItemRepository.findBySale(sale).stream()
                .map(this::toSaleItemResponse)
                .collect(Collectors.toList());

        return new SalesDtos.SaleResponse(
                sale.getId(),
                sale.getShopId(),
                sale.getBillNo(),
                sale.getCustomer() != null ? sale.getCustomer().getId() : null,
                sale.getCustomer() != null ? sale.getCustomer().getName() : null,
                sale.getCustomer() != null ? sale.getCustomer().getPhone() : null,
                sale.getUser().getId(),
                sale.getUser().getName(),
                sale.getSubtotal(),
                sale.getDiscount(),
                sale.getDiscountType().name(),
                sale.getTotalAmount(),
                sale.getPaidAmount(),
                sale.getPaymentMode().name(),
                sale.getPaymentStatus().name(),
                sale.getPaymentReference(),
                sale.isVoid(),
                sale.getVoidReason(),
                sale.getVoidedBy() != null ? sale.getVoidedBy().getId() : null,
                sale.getVoidedBy() != null ? sale.getVoidedBy().getName() : null,
                sale.getVoidedAt() != null ? sale.getVoidedAt().toString() : null,
                sale.isActive(),
                sale.getCreatedAt() != null ? sale.getCreatedAt().toString() : null,
                itemResponses
        );
    }

    private SalesDtos.SaleSummaryResponse toSaleSummaryResponse(Sale sale) {
        return new SalesDtos.SaleSummaryResponse(
                sale.getId(),
                sale.getBillNo(),
                sale.getCustomer() != null ? sale.getCustomer().getName() : "Walk-in",
                sale.getCustomer() != null ? sale.getCustomer().getPhone() : null,
                sale.getTotalAmount(),
                sale.getPaymentMode().name(),
                sale.getPaymentStatus().name(),
                sale.isVoid(),
                sale.getCreatedAt() != null ? sale.getCreatedAt().toString() : null
        );
    }

    private SalesDtos.SaleItemResponse toSaleItemResponse(SaleItem item) {
        return new SalesDtos.SaleItemResponse(
                item.getId(),
                item.getSale().getId(),
                item.getProduct().getId(),
                item.getSku(),
                item.getProductName(),
                item.getBatchId(),
                item.getQty(),
                item.getUnit(),
                item.getRate(),
                item.getAmount(),
                item.getCostPrice(),
                item.getProfit(),
                item.getCreatedAt() != null ? item.getCreatedAt().toString() : null
        );
    }

    /**
     * Zero-amount sale that ships replacement goods for a return. Stock is deducted FIFO,
     * no payment is recorded, and each line carries the real cost so P&L shows the replacement as a loss.
     */
    @Transactional
    public Sale createReplacementSale(Long shopId, Long customerId, String returnNo,
                                      Map<Product, BigDecimal> lines, Long userId) {
        User user = userService.getById(userId);
        LocalDateTime now = LocalDateTime.now();

        Sale sale = new Sale();
        sale.setShopId(shopId);
        sale.setUser(user);
        if (customerId != null) {
            sale.setCustomer(customerService.findById(customerId));
        }
        sale.setBillNo(generateBillNo(shopId));
        sale.setSubtotal(BigDecimal.ZERO);
        sale.setDiscount(BigDecimal.ZERO);
        sale.setDiscountType(Sale.DiscountType.AMOUNT);
        sale.setTotalAmount(BigDecimal.ZERO);
        sale.setPaidAmount(BigDecimal.ZERO);
        sale.setPaymentMode(Sale.PaymentMode.CASH);
        sale.setPaymentStatus(Sale.PaymentStatus.PAID);
        sale.setNotes("Replacement for return " + returnNo);
        sale.setActive(true);
        sale.setVoid(false);
        sale.setCreatedAt(now);
        sale.setUpdatedAt(now);
        saleRepository.save(sale);

        for (Map.Entry<Product, BigDecimal> line : lines.entrySet()) {
            Product product = line.getKey();
            BigDecimal qty = line.getValue();

            BigDecimal available = inventoryService.getAvailableStockExcludingReserved(shopId, product.getId());
            if (available.compareTo(qty) < 0) {
                throw new DomainException(400,
                        "Insufficient stock to replace " + product.getName()
                                + ". Available: " + available + ", Needed: " + qty);
            }

            ProductBatchStock fifoBatch = inventoryService.getOldestAvailableBatch(shopId, product.getId());

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setShopId(shopId);
            item.setProduct(product);
            item.setBatchId(fifoBatch != null ? fifoBatch.getBatchId() : null);
            item.setSku(product.getSku());
            item.setProductName(product.getName());
            item.setQty(qty);
            item.setUnit(product.getUnit().name());
            item.setRate(BigDecimal.ZERO);
            item.setAmount(BigDecimal.ZERO);
            item.setCostPrice(product.getCostPrice());
            item.setProfit(product.getCostPrice().multiply(qty).negate().setScale(2, RoundingMode.HALF_UP));
            item.setCreatedAt(now);
            saleItemRepository.save(item);

            inventoryService.deductStockFIFO(product, qty, userId, now,
                    "Replacement: " + returnNo, "SALE", sale.getId());
        }

        dashboardCache.evict(shopId);
        return sale;
    }

    @Transactional(readOnly = true)
    public Sale findById(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Sale not found"));
        // Eagerly load items to avoid lazy loading issues in PDF generation
        sale.getItems().size();
        return sale;
    }
}
