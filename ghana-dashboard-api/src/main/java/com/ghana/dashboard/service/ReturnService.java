package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.config.AppProperties;
import com.ghana.dashboard.domain.Customer;
import com.ghana.dashboard.domain.LossLedger;
import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.domain.OrderItem;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.Return;
import com.ghana.dashboard.domain.ReturnItem;
import com.ghana.dashboard.domain.ReturnPhoto;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.domain.SaleItem;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.ReturnDtos;
import com.ghana.dashboard.repository.OrderItemRepository;
import com.ghana.dashboard.repository.OrderRepository;
import com.ghana.dashboard.repository.PaymentRepository;
import com.ghana.dashboard.repository.ProductBatchStockRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.ReturnItemRepository;
import com.ghana.dashboard.repository.ReturnPhotoRepository;
import com.ghana.dashboard.repository.ReturnRepository;
import com.ghana.dashboard.repository.SaleItemRepository;
import com.ghana.dashboard.repository.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnService.class);

    private static final int MAX_PHOTOS_PER_RETURN = 6;
    private static final int TOP_BATCHES = 10;
    private static final Map<String, String> PHOTO_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final ReturnRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final ReturnPhotoRepository returnPhotoRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductBatchStockRepository batchStockRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;
    private final LossLedgerService lossLedgerService;
    private final SaleService saleService;
    private final CashBookDayService cashBookDayService;
    private final CustomerService customerService;
    private final UserService userService;
    private final AppProperties props;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public ReturnService(ReturnRepository returnRepository,
                         ReturnItemRepository returnItemRepository,
                         ReturnPhotoRepository returnPhotoRepository,
                         SaleRepository saleRepository,
                         SaleItemRepository saleItemRepository,
                         OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         ProductRepository productRepository,
                         ProductBatchStockRepository batchStockRepository,
                         PaymentRepository paymentRepository,
                         InventoryService inventoryService,
                         LossLedgerService lossLedgerService,
                         SaleService saleService,
                         CashBookDayService cashBookDayService,
                         CustomerService customerService,
                         UserService userService,
                         AppProperties props,
                         org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.returnPhotoRepository = returnPhotoRepository;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.batchStockRepository = batchStockRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryService = inventoryService;
        this.lossLedgerService = lossLedgerService;
        this.saleService = saleService;
        this.cashBookDayService = cashBookDayService;
        this.customerService = customerService;
        this.userService = userService;
        this.props = props;
    }

    // ==================== Source lookup ====================

    /** One returnable line of a bill, aggregated per product. */
    private record SourceLine(Product product, BigDecimal qty, String unit, BigDecimal rate, String batchId) {}

    private record Source(Return.SourceType type, Long id, String billNo, Customer customer, List<SourceLine> lines) {}

    private Source loadSource(Long shopId, Return.SourceType type, Long id, String billNo) {
        if (type == Return.SourceType.SALE) {
            Sale sale = (id != null ? saleRepository.findById(id) : saleRepository.findByShopIdAndBillNo(shopId, billNo))
                    .filter(s -> s.getShopId().equals(shopId))
                    .orElseThrow(() -> new DomainException(404, "Sale not found"));
            if (sale.isVoid()) {
                throw new DomainException(400, "Sale " + sale.getBillNo() + " is void and cannot be returned");
            }
            List<SourceLine> lines = aggregate(saleItemRepository.findBySale(sale).stream()
                    .map(i -> new SourceLine(i.getProduct(), i.getQty(), i.getUnit(), i.getRate(), i.getBatchId()))
                    .toList());
            return new Source(type, sale.getId(), sale.getBillNo(), sale.getCustomer(), lines);
        }

        Order order = (id != null ? orderRepository.findById(id) : orderRepository.findByShopIdAndBillNo(shopId, billNo))
                .filter(o -> o.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Order not found"));
        if (order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw new DomainException(400, "Only delivered orders can be returned. " + order.getBillNo()
                    + " is " + order.getStatus());
        }
        List<SourceLine> lines = aggregate(orderItemRepository.findByOrder(order).stream()
                .map(i -> new SourceLine(i.getProduct(), i.getQty(), i.getUnit(), i.getRate(), null))
                .toList());
        return new Source(type, order.getId(), order.getBillNo(), order.getCustomer(), lines);
    }

    /** Merge repeated lines of the same product: summed qty, first line's rate and batch. */
    private List<SourceLine> aggregate(List<SourceLine> raw) {
        Map<Long, SourceLine> merged = new LinkedHashMap<>();
        for (SourceLine line : raw) {
            merged.merge(line.product().getId(), line, (a, b) ->
                    new SourceLine(a.product(), a.qty().add(b.qty()), a.unit(), a.rate(), a.batchId()));
        }
        return new ArrayList<>(merged.values());
    }

    @Transactional(readOnly = true)
    public ReturnDtos.LookupResponse lookup(Long shopId, String billNo) {
        String bill = billNo == null ? "" : billNo.trim();
        if (bill.isEmpty()) {
            throw new DomainException(400, "Enter a bill or order number");
        }
        Return.SourceType type = bill.toUpperCase(Locale.ROOT).startsWith("O") ? Return.SourceType.ORDER : Return.SourceType.SALE;
        Source source = loadSource(shopId, type, null, bill);

        List<ReturnDtos.LookupItem> items = source.lines().stream().map(l -> {
            BigDecimal claimed = returnItemRepository.sumClaimedQty(shopId, source.type(), source.id(), l.product().getId());
            BigDecimal remaining = l.qty().subtract(claimed).max(BigDecimal.ZERO);
            return new ReturnDtos.LookupItem(l.product().getId(), l.product().getName(), l.product().getSku(),
                    remaining, l.unit(), l.rate(), l.batchId());
        }).filter(i -> i.qty().signum() > 0).toList();

        return new ReturnDtos.LookupResponse(source.type().name(), source.id(), source.billNo(),
                source.customer() != null ? source.customer().getId() : null,
                source.customer() != null ? source.customer().getName() : null,
                items);
    }

    // ==================== Create ====================

    @Transactional
    public ReturnDtos.ReturnDetailResponse create(Long shopId, Long userId, ReturnDtos.CreateReturnRequest request) {
        Return.SourceType sourceType = parseEnum(Return.SourceType.class, request.sourceType(), "source type");
        Return.ComplaintType type = parseEnum(Return.ComplaintType.class, request.type(), "complaint type");
        Source source = loadSource(shopId, sourceType, request.sourceId(), null);
        Map<Long, SourceLine> byProduct = source.lines().stream()
                .collect(Collectors.toMap(l -> l.product().getId(), Function.identity()));

        Map<Long, BigDecimal> requested = new LinkedHashMap<>();
        for (ReturnDtos.ReturnItemInput input : request.items()) {
            requested.merge(input.productId(), input.qty(), BigDecimal::add);
        }

        Return ret = new Return();
        ret.setShopId(shopId);
        ret.setReturnNo("TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        ret.setSourceType(sourceType);
        ret.setSourceId(source.id());
        ret.setCustomerId(source.customer() != null ? source.customer().getId() : null);
        ret.setType(type);
        ret.setStatus(Return.Status.OPEN);
        ret.setDescription(request.description() != null && !request.description().isBlank()
                ? request.description().trim() : null);
        ret.setReportedBy(userId);
        ret = returnRepository.save(ret);
        ret.setReturnNo(String.format("R-%s-%05d", LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")), ret.getId()));

        for (Map.Entry<Long, BigDecimal> entry : requested.entrySet()) {
            SourceLine line = byProduct.get(entry.getKey());
            if (line == null) {
                throw new DomainException(400, "Product " + entry.getKey() + " is not on bill " + source.billNo());
            }
            BigDecimal claimed = returnItemRepository.sumClaimedQty(shopId, sourceType, source.id(), entry.getKey());
            BigDecimal remaining = line.qty().subtract(claimed);
            if (entry.getValue().compareTo(remaining) > 0) {
                throw new DomainException(400, "Only " + remaining.stripTrailingZeros().toPlainString() + " of "
                        + line.product().getName() + " can still be returned on " + source.billNo());
            }

            ReturnItem item = new ReturnItem();
            item.setReturnRequest(ret);
            item.setShopId(shopId);
            item.setProduct(line.product());
            item.setBatchId(line.batchId());
            item.setQty(entry.getValue());
            item.setUnitPrice(line.rate());
            returnItemRepository.save(item);
        }

        return toDetail(ret);
    }

    // ==================== Photos ====================

    @Transactional
    public List<ReturnDtos.ReturnPhotoResponse> addPhotos(Long shopId, Long id, List<MultipartFile> files) {
        Return ret = requireReturn(shopId, id);
        if (ret.getStatus() == Return.Status.RESOLVED) {
            throw new DomainException(409, "Photos cannot be added to a resolved return");
        }
        List<MultipartFile> valid = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (valid.isEmpty()) {
            throw new DomainException(400, "Choose at least one photo");
        }
        int existing = returnPhotoRepository.findByReturnRequestOrderByIdAsc(ret).size();
        if (existing + valid.size() > MAX_PHOTOS_PER_RETURN) {
            throw new DomainException(400, "A return can have at most " + MAX_PHOTOS_PER_RETURN + " photos");
        }

        List<Path> written = new ArrayList<>();
        List<ReturnPhoto> saved = new ArrayList<>();
        try {
            for (MultipartFile file : valid) {
                StoredPhoto stored = storePhoto(shopId, file);
                written.add(stored.path());
                ReturnPhoto photo = new ReturnPhoto();
                photo.setReturnRequest(ret);
                photo.setShopId(shopId);
                photo.setFileName(stored.originalName());
                photo.setFilePath(stored.path().toString());
                photo.setFileSize(stored.size());
                photo.setFileType(stored.contentType());
                saved.add(returnPhotoRepository.save(photo));
            }
        } catch (RuntimeException e) {
            written.forEach(this::deleteQuietly);
            throw e;
        }
        return saved.stream().map(this::toPhotoResponse).toList();
    }

    public record PhotoDownload(Resource resource, String fileName, String contentType) {}

    @Transactional(readOnly = true)
    public PhotoDownload getPhoto(Long shopId, Long returnId, Long photoId) {
        Return ret = requireReturn(shopId, returnId);
        ReturnPhoto photo = returnPhotoRepository.findById(photoId)
                .filter(p -> p.getReturnRequest().getId().equals(ret.getId()))
                .orElseThrow(() -> new DomainException(404, "Photo not found"));
        Path path = Paths.get(photo.getFilePath()).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot()) || !Files.isReadable(path)) {
            throw new DomainException(404, "Photo file is missing");
        }
        return new PhotoDownload(new PathResource(path), photo.getFileName(), photo.getFileType());
    }

    private record StoredPhoto(Path path, String originalName, long size, String contentType) {}

    private StoredPhoto storePhoto(Long shopId, MultipartFile file) {
        long maxBytes = props.getStorage().getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new DomainException(413, "Each photo must be at most " + props.getStorage().getMaxFileSizeMb() + " MB");
        }
        String original = sanitizeName(file.getOriginalFilename());
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = PHOTO_TYPES.get(ext);
        if (contentType == null) {
            throw new DomainException(400, "Photos must be JPG, PNG or WEBP images");
        }

        Path dir = uploadRoot().resolve("returns").resolve(shopId.toString()).normalize();
        Path target = dir.resolve(UUID.randomUUID() + "." + ext).normalize();
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store return photo", e);
            throw new DomainException(500, "Could not save the photo");
        }
        return new StoredPhoto(target, original, file.getSize(), contentType);
    }

    private Path uploadRoot() {
        return Paths.get(props.getStorage().getUploadDir()).toAbsolutePath().normalize();
    }

    private static String sanitizeName(String name) {
        String base = name == null ? "photo" : Paths.get(name.replace('\\', '/')).getFileName().toString();
        String cleaned = base.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return cleaned.isBlank() ? "photo" : cleaned.substring(0, Math.min(cleaned.length(), 200));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete orphaned photo {}", path, e);
        }
    }

    // ==================== Review ====================

    @Transactional
    public ReturnDtos.ReturnDetailResponse review(Long shopId, Long id, ReturnDtos.ReviewRequest request) {
        Return ret = requireReturn(shopId, id);
        if (ret.getStatus() != Return.Status.OPEN) {
            throw new DomainException(409, "Only open returns can be moved to review. This one is " + ret.getStatus());
        }
        ret.setStatus(Return.Status.IN_REVIEW);
        if (request != null && request.note() != null && !request.note().isBlank()) {
            String note = "Review: " + request.note().trim();
            String merged = ret.getDescription() == null ? note : ret.getDescription() + "\n" + note;
            ret.setDescription(merged.length() > 1000 ? merged.substring(0, 1000) : merged);
        }
        return toDetail(returnRepository.save(ret));
    }

    // ==================== Resolve ====================

    @Transactional
    public ReturnDtos.ReturnDetailResponse resolve(Long shopId, Long userId, Long id, ReturnDtos.ResolveRequest request) {
        Return ret = requireReturn(shopId, id);
        if (ret.getStatus() != Return.Status.IN_REVIEW) {
            throw new DomainException(409, ret.getStatus() == Return.Status.RESOLVED
                    ? "This return is already resolved"
                    : "Move the return to review before resolving it");
        }
        Return.Resolution resolution = parseEnum(Return.Resolution.class, request.resolution(), "resolution");
        List<ReturnItem> items = returnItemRepository.findByReturnRequestOrderByIdAsc(ret);
        LocalDateTime now = LocalDateTime.now();

        if (resolution != Return.Resolution.REJECT) {
            Map<Long, ReturnItem.Action> actions = parseActions(request.items(), items);
            for (ReturnItem item : items) {
                ReturnItem.Action action = actions.get(item.getId());
                item.setAction(action);
                returnItemRepository.save(item);
                if (action == ReturnItem.Action.RESTOCK) {
                    inventoryService.restockToOriginalBatch(item.getProduct(), item.getBatchId(), item.getQty(),
                            "Return " + ret.getReturnNo(), userId, now);
                } else {
                    writeOff(shopId, userId, ret, item);
                }
            }
        }

        BigDecimal totalValue = totalValue(items);
        if (resolution == Return.Resolution.REFUND) {
            BigDecimal amount = request.refundAmount() != null ? request.refundAmount() : totalValue;
            if (amount.signum() <= 0) {
                throw new DomainException(400, "Refund amount must be greater than zero");
            }
            if (amount.compareTo(totalValue) > 0) {
                throw new DomainException(400, "Refund cannot exceed the returned goods value of " + totalValue);
            }
            recordRefund(shopId, userId, ret, amount.setScale(2, RoundingMode.HALF_UP), request.paymentMode());
            ret.setRefundAmount(amount.setScale(2, RoundingMode.HALF_UP));
        } else if (resolution == Return.Resolution.REPLACE) {
            Map<Product, BigDecimal> lines = new LinkedHashMap<>();
            for (ReturnItem item : items) {
                lines.merge(item.getProduct(), item.getQty(), BigDecimal::add);
            }
            Sale replacement = saleService.createReplacementSale(shopId, ret.getCustomerId(), ret.getReturnNo(), lines, userId);
            ret.setReplacementSaleId(replacement.getId());
        }

        ret.setResolution(resolution);
        ret.setStatus(Return.Status.RESOLVED);
        ret.setResolvedBy(userId);
        ret.setResolvedAt(now);
        if (request.note() != null && !request.note().isBlank()) {
            String note = "Resolution: " + request.note().trim();
            String merged = ret.getDescription() == null ? note : ret.getDescription() + "\n" + note;
            ret.setDescription(merged.length() > 1000 ? merged.substring(0, 1000) : merged);
        }
        Return saved = returnRepository.save(ret);
        if (saved.getCustomerId() != null) {
            eventPublisher.publishEvent(new com.ghana.dashboard.event.CustomerActivityEvent(shopId, saved.getCustomerId()));
        }
        return toDetail(saved);
    }

    private Map<Long, ReturnItem.Action> parseActions(List<ReturnDtos.ResolveItemInput> inputs, List<ReturnItem> items) {
        if (inputs == null || inputs.isEmpty()) {
            throw new DomainException(400, "Choose restock or write-off for every returned item");
        }
        Map<Long, ReturnItem.Action> actions = new HashMap<>();
        for (ReturnDtos.ResolveItemInput input : inputs) {
            if (items.stream().noneMatch(i -> i.getId().equals(input.returnItemId()))) {
                throw new DomainException(400, "Item " + input.returnItemId() + " is not part of this return");
            }
            if (actions.put(input.returnItemId(), parseEnum(ReturnItem.Action.class, input.action(), "item action")) != null) {
                throw new DomainException(400, "Item " + input.returnItemId() + " was given more than once");
            }
        }
        for (ReturnItem item : items) {
            if (!actions.containsKey(item.getId())) {
                throw new DomainException(400, "Choose restock or write-off for " + item.getProduct().getName());
            }
        }
        return actions;
    }

    /** Returned goods that cannot be resold are a loss at cost, not at selling price. */
    private void writeOff(Long shopId, Long userId, Return ret, ReturnItem item) {
        BigDecimal unitCost = item.getBatchId() == null ? null
                : batchStockRepository.findByShopIdAndProductIdAndBatchId(shopId, item.getProduct().getId(), item.getBatchId())
                        .map(ProductBatchStock::getCostPrice).orElse(null);
        if (unitCost == null) {
            unitCost = item.getProduct().getCostPrice();
        }
        BigDecimal value = unitCost.multiply(item.getQty()).setScale(2, RoundingMode.HALF_UP);
        LossLedger loss = lossLedgerService.recordLoss(shopId, LossLedger.LossType.RETURN_WRITE_OFF,
                item.getProduct().getId(), item.getBatchId(), item.getQty(), value,
                "Return " + ret.getReturnNo() + " (" + ret.getType() + ") written off",
                DateDefaults.today(), userId);
        loss.setReferenceNo(ret.getReturnNo());
    }

    private void recordRefund(Long shopId, Long userId, Return ret, BigDecimal amount, String modeName) {
        LocalDate today = DateDefaults.today();
        cashBookDayService.verifyDayNotClosed(shopId, today, userId);

        Payment payment = new Payment();
        payment.setShopId(shopId);
        payment.setDirection(Payment.Direction.OUT);
        payment.setMode(modeName == null || modeName.isBlank()
                ? Payment.PaymentMode.CASH
                : parseEnum(Payment.PaymentMode.class, modeName, "payment mode"));
        payment.setAmount(amount);
        payment.setSourceType(Payment.SourceType.REFUND);
        payment.setSourceId(ret.getId());
        payment.setReferenceNo(ret.getReturnNo());
        payment.setPaymentDate(today);
        payment.setNote("Refund for return " + ret.getReturnNo());
        payment.setCreatedBy(userId);
        Payment saved = paymentRepository.save(payment);
        cashBookDayService.recordPaymentForDay(shopId, saved);
    }

    // ==================== Queries ====================

    @Transactional(readOnly = true)
    public List<ReturnDtos.ReturnSummaryResponse> list(Long shopId, ReturnDtos.ReturnFilter filter) {
        LocalDate from = filter.dateFrom() != null ? filter.dateFrom() : DateDefaults.today().minusMonths(3);
        LocalDate to = filter.dateTo() != null ? filter.dateTo() : DateDefaults.today();
        DateDefaults.requireOrdered(from, to);

        Return.Status status = filter.status() == null || filter.status().isBlank() ? null
                : parseEnum(Return.Status.class, filter.status(), "status");
        Return.ComplaintType type = filter.type() == null || filter.type().isBlank() ? null
                : parseEnum(Return.ComplaintType.class, filter.type(), "type");

        List<Return> returns = returnRepository
                .findByShopIdAndCreatedAtBetweenOrderByCreatedAtDesc(shopId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> type == null || r.getType() == type)
                .toList();
        if (returns.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ReturnItem>> itemsByReturn = returnItemRepository.findByReturnRequestIn(returns).stream()
                .collect(Collectors.groupingBy(i -> i.getReturnRequest().getId()));
        Map<Long, List<Long>> photoIds = returnPhotoRepository.findByReturnRequestIn(returns).stream()
                .sorted(Comparator.comparing(ReturnPhoto::getId))
                .collect(Collectors.groupingBy(p -> p.getReturnRequest().getId(),
                        Collectors.mapping(ReturnPhoto::getId, Collectors.toList())));
        Map<String, String> billNos = billNos(returns);
        Map<Long, String> customers = customerNames(returns);

        return returns.stream().map(r -> {
            List<ReturnItem> items = itemsByReturn.getOrDefault(r.getId(), List.of());
            return new ReturnDtos.ReturnSummaryResponse(
                    r.getId(), r.getReturnNo(), r.getSourceType().name(), r.getSourceId(),
                    billNos.get(billKey(r)), r.getCustomerId(), customers.get(r.getCustomerId()),
                    r.getType().name(), r.getStatus().name(),
                    r.getResolution() != null ? r.getResolution().name() : null,
                    items.size(), totalValue(items), photoIds.getOrDefault(r.getId(), List.of()),
                    String.valueOf(r.getCreatedAt()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public ReturnDtos.ReturnDetailResponse get(Long shopId, Long id) {
        return toDetail(requireReturn(shopId, id));
    }

    @Transactional(readOnly = true)
    public ReturnDtos.ReturnStatsResponse stats(Long shopId, LocalDate dateFrom, LocalDate dateTo) {
        LocalDate from = dateFrom != null ? dateFrom : DateDefaults.today().minusMonths(3);
        LocalDate to = dateTo != null ? dateTo : DateDefaults.today();
        DateDefaults.requireOrdered(from, to);

        List<Return> returns = returnRepository
                .findByShopIdAndCreatedAtBetweenOrderByCreatedAtDesc(shopId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        if (returns.isEmpty()) {
            return new ReturnDtos.ReturnStatsResponse(List.of(), List.of());
        }
        Map<Long, List<ReturnItem>> itemsByReturn = returnItemRepository.findByReturnRequestIn(returns).stream()
                .collect(Collectors.groupingBy(i -> i.getReturnRequest().getId()));

        Map<Return.ComplaintType, long[]> typeCounts = new LinkedHashMap<>();
        Map<Return.ComplaintType, BigDecimal> typeValues = new LinkedHashMap<>();
        // key: product + batch; a complaint counts once per batch even if it lists several lines of it
        Map<String, Long> batchCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> batchValues = new HashMap<>();
        Map<String, String[]> batchLabels = new HashMap<>();

        for (Return r : returns) {
            List<ReturnItem> items = itemsByReturn.getOrDefault(r.getId(), List.of());
            typeCounts.computeIfAbsent(r.getType(), k -> new long[1])[0]++;
            typeValues.merge(r.getType(), totalValue(items), BigDecimal::add);

            Map<String, Boolean> seen = new HashMap<>();
            for (ReturnItem item : items) {
                if (item.getBatchId() == null) {
                    continue;
                }
                String key = item.getProduct().getId() + "|" + item.getBatchId();
                batchLabels.putIfAbsent(key, new String[]{item.getBatchId(), item.getProduct().getName()});
                batchValues.merge(key, item.getQty().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
                if (seen.put(key, true) == null) {
                    batchCounts.merge(key, 1L, Long::sum);
                }
            }
        }

        List<ReturnDtos.ReturnTypeStat> byType = typeCounts.entrySet().stream()
                .map(e -> new ReturnDtos.ReturnTypeStat(e.getKey().name(), e.getValue()[0], typeValues.get(e.getKey())))
                .sorted(Comparator.comparingLong(ReturnDtos.ReturnTypeStat::count).reversed())
                .toList();
        List<ReturnDtos.ReturnBatchStat> byBatch = batchCounts.entrySet().stream()
                .map(e -> new ReturnDtos.ReturnBatchStat(batchLabels.get(e.getKey())[0], batchLabels.get(e.getKey())[1],
                        e.getValue(), batchValues.get(e.getKey())))
                .sorted(Comparator.comparingLong(ReturnDtos.ReturnBatchStat::count).reversed()
                        .thenComparing(ReturnDtos.ReturnBatchStat::totalValue, Comparator.reverseOrder()))
                .limit(TOP_BATCHES)
                .toList();
        return new ReturnDtos.ReturnStatsResponse(byType, byBatch);
    }

    // ==================== Helpers ====================

    private Return requireReturn(Long shopId, Long id) {
        return returnRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new DomainException(404, "Return not found"));
    }

    private static BigDecimal totalValue(Collection<ReturnItem> items) {
        return items.stream()
                .map(i -> i.getQty().multiply(i.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException(400, "Invalid " + label + ": " + value);
        }
    }

    private static String billKey(Return r) {
        return r.getSourceType() + ":" + r.getSourceId();
    }

    private Map<String, String> billNos(List<Return> returns) {
        Map<String, String> byKey = new HashMap<>();
        List<Long> saleIds = returns.stream().filter(r -> r.getSourceType() == Return.SourceType.SALE)
                .map(Return::getSourceId).distinct().toList();
        List<Long> orderIds = returns.stream().filter(r -> r.getSourceType() == Return.SourceType.ORDER)
                .map(Return::getSourceId).distinct().toList();
        saleRepository.findAllById(saleIds).forEach(s -> byKey.put("SALE:" + s.getId(), s.getBillNo()));
        orderRepository.findAllById(orderIds).forEach(o -> byKey.put("ORDER:" + o.getId(), o.getBillNo()));
        return byKey;
    }

    private Map<Long, String> customerNames(List<Return> returns) {
        List<Long> ids = returns.stream().map(Return::getCustomerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        for (Long cid : ids) {
            names.put(cid, customerService.findById(cid).getName());
        }
        return names;
    }

    private ReturnDtos.ReturnPhotoResponse toPhotoResponse(ReturnPhoto p) {
        return new ReturnDtos.ReturnPhotoResponse(p.getId(), p.getFileName(), p.getFileSize(), p.getFileType());
    }

    private ReturnDtos.ReturnDetailResponse toDetail(Return r) {
        List<ReturnItem> items = returnItemRepository.findByReturnRequestOrderByIdAsc(r);
        List<ReturnPhoto> photos = returnPhotoRepository.findByReturnRequestOrderByIdAsc(r);
        String billNo = r.getSourceType() == Return.SourceType.SALE
                ? saleRepository.findById(r.getSourceId()).map(Sale::getBillNo).orElse(null)
                : orderRepository.findById(r.getSourceId()).map(Order::getBillNo).orElse(null);
        String customerName = r.getCustomerId() != null ? customerService.findById(r.getCustomerId()).getName() : null;
        User reporter = userService.findById(r.getReportedBy()).orElse(null);
        User resolver = r.getResolvedBy() != null ? userService.findById(r.getResolvedBy()).orElse(null) : null;

        return new ReturnDtos.ReturnDetailResponse(
                r.getId(), r.getReturnNo(), r.getSourceType().name(), r.getSourceId(), billNo,
                r.getCustomerId(), customerName, r.getType().name(), r.getStatus().name(),
                r.getResolution() != null ? r.getResolution().name() : null,
                r.getDescription(), r.getRefundAmount(), r.getReplacementSaleId(),
                items.stream().map(i -> new ReturnDtos.ReturnItemResponse(
                        i.getId(), i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getSku(),
                        i.getBatchId(), i.getQty(), i.getUnitPrice(),
                        i.getAction() != null ? i.getAction().name() : null)).toList(),
                photos.stream().map(this::toPhotoResponse).toList(),
                reporter != null ? reporter.getName() : "",
                resolver != null ? resolver.getName() : null,
                r.getResolvedAt() != null ? r.getResolvedAt().toString() : null,
                String.valueOf(r.getCreatedAt()));
    }
}
