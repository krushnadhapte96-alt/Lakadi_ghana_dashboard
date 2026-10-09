package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.*;
import com.ghana.dashboard.dto.SalesDtos;
import com.ghana.dashboard.event.SaleOnCreditEvent;
import com.ghana.dashboard.repository.*;
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
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final KhataLedgerService khataLedgerService;
    private final DashboardCache dashboardCache;

    public OrderService(OrderRepository orderRepository,
                       OrderItemRepository orderItemRepository,
                       OrderStatusHistoryRepository orderStatusHistoryRepository,
                       ProductRepository productRepository,
                       InventoryService inventoryService,
                       CustomerService customerService,
                       UserService userService,
                       PaymentService paymentService,
                       ApplicationEventPublisher eventPublisher,
                       KhataLedgerService khataLedgerService,
                       DashboardCache dashboardCache) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.userService = userService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.khataLedgerService = khataLedgerService;
        this.dashboardCache = dashboardCache;
    }

    // ==================== Bill Number Generation ====================

    private String generateBillNo(Long shopId) {
        String prefix = "O";
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(23, 59, 59);
        long count = orderRepository.countByShopIdAndCreatedAtBetween(shopId, todayStart, todayEnd) + 1;
        
        return prefix + "-" + datePart + "-" + String.format("%04d", count);
    }

    // ==================== Order CRUD ====================

    @Transactional
    public SalesDtos.OrderResponse createOrder(Long shopId, SalesDtos.OrderRequest request, Long userId) {
        User user = userService.getById(userId);
        Customer customer = customerService.findById(request.customerId());
        CustomerAddress address = customerService.findAddressById(request.addressId());

        // Validate customer and address belong to shop
        if (!customer.getShopId().equals(shopId)) {
            throw new DomainException(403, "Customer does not belong to this shop");
        }
        if (!address.getShopId().equals(shopId)) {
            throw new DomainException(403, "Address does not belong to this shop");
        }

        Order order = new Order();
        order.setShopId(shopId);
        order.setUser(user);
        order.setCustomer(customer);
        order.setAddress(address);
        order.setBillNo(generateBillNo(shopId));

        // Calculate subtotal and validate items
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        for (SalesDtos.OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .filter(p -> p.getShopId().equals(shopId))
                    .orElseThrow(() -> new DomainException(400, "Product not found or does not belong to this shop"));

            if (!product.isActive()) {
                throw new DomainException(400, "Product " + product.getName() + " is inactive");
            }

            // For orders, we don't check stock yet - will check on CONFIRM
            BigDecimal amount = itemRequest.rate().multiply(itemRequest.qty()).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(amount);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setShopId(shopId);
            orderItem.setProduct(product);
            orderItem.setSku(product.getSku());
            orderItem.setProductName(product.getName());
            orderItem.setQty(itemRequest.qty());
            orderItem.setUnit(product.getUnit().name());
            orderItem.setRate(itemRequest.rate());
            orderItem.setAmount(amount);
            orderItem.setCostPrice(product.getCostPrice());
            orderItem.setProfit(amount.subtract(product.getCostPrice().multiply(itemRequest.qty())));
            orderItem.setCreatedAt(LocalDateTime.now());
            
            orderItems.add(orderItem);
        }

        // Calculate discount and total
        BigDecimal discount = request.discount();
        BigDecimal totalAmount = subtotal;
        
        if (Order.DiscountType.PERCENT.name().equals(request.discountType())) {
            discount = subtotal.multiply(discount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalAmount = subtotal.subtract(discount);
        } else {
            totalAmount = subtotal.subtract(discount);
        }

        totalAmount = totalAmount.add(request.deliveryCharge());

        order.setSubtotal(subtotal);
        order.setDeliveryCharge(request.deliveryCharge());
        order.setDiscount(discount);
        order.setDiscountType(Order.DiscountType.valueOf(request.discountType()));
        order.setTotalAmount(totalAmount);
        order.setPaidAmount(request.paidAmount() != null ? request.paidAmount() : BigDecimal.ZERO);
        order.setPaymentMode(Order.PaymentMode.valueOf(request.paymentMode()));
        
        // For orders, payment status starts as UNPAID unless paid
        if (Order.PaymentMode.COD.equals(order.getPaymentMode())) {
            order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        } else if (Order.PaymentMode.UDHAAR.equals(order.getPaymentMode())) {
            order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        } else if (order.getPaidAmount().compareTo(totalAmount) >= 0) {
            order.setPaymentStatus(Order.PaymentStatus.PAID);
        } else if (order.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(Order.PaymentStatus.PARTIAL);
        } else {
            order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        }

        order.setPaymentReference(request.paymentReference());
        order.setStatus(Order.OrderStatus.NEW);
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setNotes(request.notes());
        order.setActive(true);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        if (Order.PaymentMode.UDHAAR.equals(order.getPaymentMode())) {
            khataLedgerService.assertCreditAllowed(shopId, customer.getPhone(), totalAmount,
                    Boolean.TRUE.equals(request.overrideCreditLimit()));
        }

        orderRepository.save(order);

        // Save order items
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
            orderItemRepository.save(orderItem);
        }

        // Add status history
        addStatusHistory(order, null, Order.OrderStatus.NEW, userId, "Order created");

        // Announce the order to the whole shop - this is the alert that plays a sound
        eventPublisher.publishEvent(new com.ghana.dashboard.event.OrderPlacedEvent(
                order.getShopId(), order.getId(), order.getBillNo(),
                customer.getName(), order.getTotalAmount(),
                orderItems.size(), order.getExpectedDeliveryDate()));

        // Publish UDHAAR event if payment mode is UDHAAR
        if (Order.PaymentMode.UDHAAR.equals(order.getPaymentMode())) {
            eventPublisher.publishEvent(new SaleOnCreditEvent(
                    this, order.getShopId(), order.getId(), order.getBillNo(),
                    order.getCustomer() != null ? order.getCustomer().getId() : null,
                    order.getCustomer() != null ? order.getCustomer().getName() : null,
                    order.getCustomer() != null ? order.getCustomer().getPhone() : null,
                    order.getTotalAmount(), "ORDER", order.getCreatedAt()));
        }

        dashboardCache.evict(shopId);
        return toOrderResponse(order);
    }

    @Transactional
    public SalesDtos.OrderResponse updateOrder(Long shopId, Long id, 
            SalesDtos.OrderUpdateRequest request, Long userId) {
        Order order = orderRepository.findById(id)
                .filter(o -> o.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Order not found"));

        if (!Order.OrderStatus.NEW.equals(order.getStatus())) {
            throw new DomainException(400, "Order can only be edited while in NEW status");
        }

        Customer customer = customerService.findById(request.customerId());
        CustomerAddress address = customerService.findAddressById(request.addressId());

        if (!customer.getShopId().equals(shopId)) {
            throw new DomainException(403, "Customer does not belong to this shop");
        }
        if (!address.getShopId().equals(shopId)) {
            throw new DomainException(403, "Address does not belong to this shop");
        }

        // Delete existing items
        orderItemRepository.deleteAll(orderItemRepository.findByOrder(order));

        // Recalculate with new items
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        for (SalesDtos.OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .filter(p -> p.getShopId().equals(shopId))
                    .orElseThrow(() -> new DomainException(400, "Product not found"));

            BigDecimal amount = itemRequest.rate().multiply(itemRequest.qty()).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(amount);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setShopId(shopId);
            orderItem.setProduct(product);
            orderItem.setSku(product.getSku());
            orderItem.setProductName(product.getName());
            orderItem.setQty(itemRequest.qty());
            orderItem.setUnit(product.getUnit().name());
            orderItem.setRate(itemRequest.rate());
            orderItem.setAmount(amount);
            orderItem.setCostPrice(product.getCostPrice());
            orderItem.setProfit(amount.subtract(product.getCostPrice().multiply(itemRequest.qty())));
            orderItem.setCreatedAt(LocalDateTime.now());
            
            orderItems.add(orderItem);
        }

        // Calculate discount and total
        BigDecimal discount = request.discount();
        BigDecimal totalAmount = subtotal;
        
        if (Order.DiscountType.PERCENT.name().equals(request.discountType())) {
            discount = subtotal.multiply(discount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalAmount = subtotal.subtract(discount);
        } else {
            totalAmount = subtotal.subtract(discount);
        }

        totalAmount = totalAmount.add(request.deliveryCharge());

        order.setCustomer(customer);
        order.setAddress(address);
        order.setSubtotal(subtotal);
        order.setDeliveryCharge(request.deliveryCharge());
        order.setDiscount(discount);
        order.setDiscountType(Order.DiscountType.valueOf(request.discountType()));
        order.setTotalAmount(totalAmount);
        order.setPaidAmount(request.paidAmount() != null ? request.paidAmount() : BigDecimal.ZERO);
        order.setPaymentMode(Order.PaymentMode.valueOf(request.paymentMode()));
        order.setPaymentReference(request.paymentReference());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setNotes(request.notes());
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);

        // Save new order items
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
            orderItemRepository.save(orderItem);
        }

        dashboardCache.evict(shopId);
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public SalesDtos.OrderResponse getOrder(Long shopId, Long id) {
        return orderRepository.findById(id)
                .filter(o -> o.getShopId().equals(shopId))
                .map(this::toOrderResponse)
                .orElseThrow(() -> new DomainException(404, "Order not found"));
    }

    @Transactional(readOnly = true)
    public Page<SalesDtos.OrderSummaryResponse> getOrders(Long shopId, SalesDtos.OrderFilter filter, Pageable pageable) {
        List<Order> orders;
        
        if (filter.customerId() != null) {
            orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(filter.customerId());
        } else {
            orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopId);
        }

        List<SalesDtos.OrderSummaryResponse> responses = orders.stream()
                .filter(o -> {
                    if (filter.status() != null && !o.getStatus().name().equals(filter.status())) {
                        return false;
                    }
                    return true;
                })
                .map(this::toOrderSummaryResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, responses.size());
    }

    @Transactional
    public SalesDtos.OrderResponse updateOrderStatus(Long shopId, Long orderId,
            SalesDtos.OrderStatusUpdateRequest request, Long userId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Order not found"));

        Order.OrderStatus oldStatus = order.getStatus();
        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(request.newStatus());

        // Validate status transition
        if (!isValidStatusTransition(oldStatus, newStatus)) {
            throw new DomainException(400, "Invalid status transition from " + oldStatus + " to " + newStatus);
        }

        // Handle CONFIRM - reserve stock
        if (Order.OrderStatus.CONFIRMED.equals(newStatus) && Order.OrderStatus.NEW.equals(oldStatus)) {
            reserveOrderStock(order, userId);
        }

        // Handle DELIVERED - deduct stock
        if (Order.OrderStatus.DELIVERED.equals(newStatus) && Order.OrderStatus.OUT_FOR_DELIVERY.equals(oldStatus)) {
            deductOrderStock(order, userId);
            order.setActualDeliveryDate(LocalDate.now());
            
            // Create auto payment for COD/CASH/UPI orders
            Payment autoPayment = paymentService.createAutoPaymentForOrder(order);
        }

        // Handle CANCELLED - release stock
        if (Order.OrderStatus.CANCELLED.equals(newStatus)) {
            eventPublisher.publishEvent(new com.ghana.dashboard.event.SaleVoidedEvent(shopId, orderId, "ORDER"));
            releaseOrderStock(order, userId);
            order.setCancelledBy(userService.getById(userId));
            order.setCancelledAt(LocalDateTime.now());
            order.setActive(false);
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Add status history
        addStatusHistory(order, oldStatus, newStatus, userId, request.notes());

        if (order.getCustomer() != null && (Order.OrderStatus.DELIVERED.equals(newStatus) || Order.OrderStatus.CANCELLED.equals(newStatus))) {
            eventPublisher.publishEvent(new com.ghana.dashboard.event.CustomerActivityEvent(shopId, order.getCustomer().getId()));
        }

        dashboardCache.evict(shopId);
        return toOrderResponse(order);
    }

    @Transactional
    public SalesDtos.OrderResponse cancelOrder(Long shopId, Long orderId,
            SalesDtos.OrderCancelRequest request, Long userId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Order not found"));

        if (!Order.OrderStatus.NEW.equals(order.getStatus()) && 
            !Order.OrderStatus.CONFIRMED.equals(order.getStatus()) &&
            !Order.OrderStatus.PACKED.equals(order.getStatus())) {
            throw new DomainException(400, "Order can only be cancelled in NEW, CONFIRMED or PACKED status");
        }

        SalesDtos.OrderStatusUpdateRequest statusRequest = new SalesDtos.OrderStatusUpdateRequest(
                "CANCELLED", "Cancelled: " + request.reason());
        
        return updateOrderStatus(shopId, orderId, statusRequest, userId);
    }

    // ==================== Status Transition Validation ====================

    private boolean isValidStatusTransition(Order.OrderStatus oldStatus, Order.OrderStatus newStatus) {
        return switch (oldStatus) {
            case NEW -> List.of(Order.OrderStatus.CONFIRMED, Order.OrderStatus.CANCELLED).contains(newStatus);
            case CONFIRMED -> List.of(Order.OrderStatus.PACKED, Order.OrderStatus.CANCELLED).contains(newStatus);
            case PACKED -> List.of(Order.OrderStatus.OUT_FOR_DELIVERY, Order.OrderStatus.CANCELLED).contains(newStatus);
            case OUT_FOR_DELIVERY -> List.of(Order.OrderStatus.DELIVERED, Order.OrderStatus.CANCELLED).contains(newStatus);
            case DELIVERED -> false; // Cannot transition from DELIVERED
            case CANCELLED -> false; // Cannot transition from CANCELLED
        };
    }

    // ==================== Stock Management ====================

    private void reserveOrderStock(Order order, Long userId) {
        List<OrderItem> items = orderItemRepository.findByOrder(order);
        for (OrderItem item : items) {
            Product product = item.getProduct();
            BigDecimal available = inventoryService.getAvailableStockExcludingReserved(order.getShopId(), product.getId());
            
            if (available.compareTo(item.getQty()) < 0) {
                throw new DomainException(400, 
                        "Insufficient stock for product " + product.getName() + 
                        ". Available: " + available + ", Requested: " + item.getQty());
            }
            
            inventoryService.reserveStock(
                    product, item.getQty(), userId, LocalDateTime.now(),
                    "ORDER_RESERVE", order.getId());
        }
    }

    private void deductOrderStock(Order order, Long userId) {
        List<OrderItem> items = orderItemRepository.findByOrder(order);
        for (OrderItem item : items) {
            Product product = item.getProduct();
            // Stock was already reserved on CONFIRM, now we just need to release and deduct
            // Actually, we need to deduct from reserved stock
            inventoryService.deductStockFIFO(
                    product, item.getQty(), userId, LocalDateTime.now(),
                    "Order: " + order.getBillNo(), "ORDER", order.getId());
        }
    }

    private void releaseOrderStock(Order order, Long userId) {
        List<OrderItem> items = orderItemRepository.findByOrder(order);
        for (OrderItem item : items) {
            Product product = item.getProduct();
            inventoryService.releaseReservation(
                    product, item.getQty(), userId, LocalDateTime.now(),
                    "ORDER_RELEASE", order.getId());
        }
    }

    // ==================== Status History ====================

    private void addStatusHistory(Order order, Order.OrderStatus oldStatus, 
                                  Order.OrderStatus newStatus, Long userId, String notes) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setShopId(order.getShopId());
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(userService.getById(userId));
        history.setNotes(notes);
        history.setCreatedAt(LocalDateTime.now());
        
        orderStatusHistoryRepository.save(history);
    }

    // ==================== Helper Methods ====================

    private SalesDtos.OrderResponse toOrderResponse(Order order) {
        List<SalesDtos.OrderItemResponse> itemResponses = orderItemRepository.findByOrder(order).stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.toList());

        List<SalesDtos.OrderStatusHistoryResponse> historyResponses = 
                orderStatusHistoryRepository.findByOrderOrderByCreatedAtAsc(order).stream()
                .map(this::toOrderStatusHistoryResponse)
                .collect(Collectors.toList());

        String addressSummary = order.getAddress() != null ?
                order.getAddress().getAddressLine1() + ", " + 
                order.getAddress().getCity() + ", " + 
                order.getAddress().getState() : null;

        return new SalesDtos.OrderResponse(
                order.getId(),
                order.getShopId(),
                order.getBillNo(),
                order.getCustomer().getId(),
                order.getCustomer().getName(),
                order.getCustomer().getPhone(),
                order.getAddress() != null ? order.getAddress().getId() : null,
                addressSummary,
                order.getUser().getId(),
                order.getUser().getName(),
                order.getSubtotal(),
                order.getDeliveryCharge(),
                order.getDiscount(),
                order.getDiscountType().name(),
                order.getTotalAmount(),
                order.getPaidAmount(),
                order.getPaymentMode().name(),
                order.getPaymentStatus().name(),
                order.getPaymentReference(),
                order.getStatus().name(),
                order.getExpectedDeliveryDate(),
                order.getActualDeliveryDate(),
                order.getNotes(),
                order.getCancelReason(),
                order.getCancelledBy() != null ? order.getCancelledBy().getId() : null,
                order.getCancelledBy() != null ? order.getCancelledBy().getName() : null,
                order.getCancelledAt() != null ? order.getCancelledAt().toString() : null,
                order.isActive(),
                order.getCreatedAt() != null ? order.getCreatedAt().toString() : null,
                itemResponses,
                historyResponses
        );
    }

    private SalesDtos.OrderSummaryResponse toOrderSummaryResponse(Order order) {
        return new SalesDtos.OrderSummaryResponse(
                order.getId(),
                order.getBillNo(),
                order.getCustomer().getName(),
                order.getCustomer().getPhone(),
                order.getTotalAmount(),
                order.getPaymentMode().name(),
                order.getPaymentStatus().name(),
                order.getStatus().name(),
                order.getExpectedDeliveryDate(),
                order.getCreatedAt() != null ? order.getCreatedAt().toString() : null
        );
    }

    private SalesDtos.OrderItemResponse toOrderItemResponse(OrderItem item) {
        return new SalesDtos.OrderItemResponse(
                item.getId(),
                item.getOrder().getId(),
                item.getProduct().getId(),
                item.getSku(),
                item.getProductName(),
                item.getQty(),
                item.getUnit(),
                item.getRate(),
                item.getAmount(),
                item.getCostPrice(),
                item.getProfit(),
                item.getCreatedAt() != null ? item.getCreatedAt().toString() : null
        );
    }

    private SalesDtos.OrderStatusHistoryResponse toOrderStatusHistoryResponse(OrderStatusHistory history) {
        return new SalesDtos.OrderStatusHistoryResponse(
                history.getId(),
                history.getOrder().getId(),
                history.getOldStatus() != null ? history.getOldStatus().name() : null,
                history.getNewStatus().name(),
                history.getChangedBy().getId(),
                history.getChangedBy().getName(),
                history.getNotes(),
                history.getCreatedAt() != null ? history.getCreatedAt().toString() : null
        );
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Order not found"));
        // Eagerly load items to avoid lazy loading issues in PDF generation
        order.getItems().size();
        return order;
    }

    @Transactional(readOnly = true)
    public long countByStatus(Long shopId, String status) {
        return orderRepository.countByShopIdAndStatus(shopId, Order.OrderStatus.valueOf(status));
    }
}
