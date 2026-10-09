package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.BatchPriceOverride;
import com.ghana.dashboard.domain.ExpiryAction;
import com.ghana.dashboard.domain.LossLedger;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.ExpiryAlertDtos;
import com.ghana.dashboard.repository.BatchPriceOverrideRepository;
import com.ghana.dashboard.repository.ExpiryActionRepository;
import com.ghana.dashboard.repository.ProductBatchStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alerts on stock lots (product_batch_stock rows) that are close to their best-before/expiry
 * date. Buckets are non-overlapping: EXPIRED, then D7 (0-7 days), D15 (8-15) and D30 (16-30).
 * A lot with an active snooze, or further than 30 days out, is left out of the results.
 */
@Service
public class ExpiryAlertsService {

    private static final String EXPIRED = "EXPIRED";
    private static final String D7 = "D7";
    private static final String D15 = "D15";
    private static final String D30 = "D30";
    private static final List<String> BUCKET_ORDER = List.of(EXPIRED, D7, D15, D30);

    private final ProductBatchStockRepository batchStockRepository;
    private final BatchPriceOverrideRepository batchPriceOverrideRepository;
    private final ExpiryActionRepository expiryActionRepository;
    private final LossLedgerService lossLedgerService;
    private final InventoryService inventoryService;
    private final UserService userService;

    public ExpiryAlertsService(ProductBatchStockRepository batchStockRepository,
                               BatchPriceOverrideRepository batchPriceOverrideRepository,
                               ExpiryActionRepository expiryActionRepository,
                               LossLedgerService lossLedgerService,
                               InventoryService inventoryService,
                               UserService userService) {
        this.batchStockRepository = batchStockRepository;
        this.batchPriceOverrideRepository = batchPriceOverrideRepository;
        this.expiryActionRepository = expiryActionRepository;
        this.lossLedgerService = lossLedgerService;
        this.inventoryService = inventoryService;
        this.userService = userService;
    }

    // ==================== GET /expiry-alerts ====================

    @Transactional(readOnly = true)
    public ExpiryAlertDtos.ExpiryAlertsResponse getAlerts(Long shopId) {
        List<ExpiryAlertDtos.ExpiryAlertItemResponse> items = alertableItems(shopId, LocalDate.now());

        Map<String, List<ExpiryAlertDtos.ExpiryAlertItemResponse>> byBucket = new LinkedHashMap<>();
        for (String bucket : BUCKET_ORDER) {
            byBucket.put(bucket, new ArrayList<>());
        }
        for (ExpiryAlertDtos.ExpiryAlertItemResponse item : items) {
            byBucket.get(item.bucket()).add(item);
        }

        List<ExpiryAlertDtos.ExpiryBucketResponse> buckets = new ArrayList<>();
        BigDecimal totalUnits = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (String bucket : BUCKET_ORDER) {
            List<ExpiryAlertDtos.ExpiryAlertItemResponse> bucketItems = byBucket.get(bucket);
            BigDecimal units = bucketItems.stream().map(ExpiryAlertDtos.ExpiryAlertItemResponse::qtyAvailable)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal value = bucketItems.stream().map(ExpiryAlertDtos.ExpiryAlertItemResponse::valueAtRisk)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalUnits = totalUnits.add(units);
            totalValue = totalValue.add(value);
            buckets.add(new ExpiryAlertDtos.ExpiryBucketResponse(bucket, bucketLabel(bucket), units, value, bucketItems));
        }

        return new ExpiryAlertDtos.ExpiryAlertsResponse(totalUnits, totalValue, buckets);
    }

    /** Rows found here are what the 07:00 sweep also computes (notifications wired in a later prompt). */
    @Transactional(readOnly = true)
    List<ExpiryAlertDtos.ExpiryAlertItemResponse> alertableItems(Long shopId, LocalDate today) {
        Set<Long> snoozed = expiryActionRepository
                .findByShopIdAndActionTypeAndSnoozedUntilGreaterThanEqual(shopId, ExpiryAction.ActionType.SNOOZE, today)
                .stream().map(a -> a.getBatchStock().getId()).collect(java.util.stream.Collectors.toSet());

        List<ExpiryAlertDtos.ExpiryAlertItemResponse> items = new ArrayList<>();
        for (ProductBatchStock stock : batchStockRepository.findByShopIdAndActiveTrueOrderByProductIdAsc(shopId)) {
            if (stock.getQtyAvailable().signum() <= 0 || snoozed.contains(stock.getId())) {
                continue;
            }
            LocalDate expiry = stock.getExpiryDate() != null ? stock.getExpiryDate() : stock.getBestBeforeDate();
            if (expiry == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, expiry);
            String bucket = bucketOf(daysLeft);
            if (bucket == null) {
                continue;
            }

            Product product = stock.getProduct();
            BigDecimal valueAtRisk = stock.getQtyAvailable().multiply(stock.getCostPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal discountPct = batchPriceOverrideRepository.findByBatchStockId(stock.getId())
                    .map(BatchPriceOverride::getDiscountPct).orElse(null);

            items.add(new ExpiryAlertDtos.ExpiryAlertItemResponse(
                    stock.getId(), stock.getBatchId(), product.getId(), product.getName(), product.getSku(),
                    stock.getQtyAvailable(), product.getUnit().name(), stock.getCostPrice(), valueAtRisk,
                    expiry, daysLeft, bucket, discountPct
            ));
        }
        items.sort(java.util.Comparator.comparingLong(ExpiryAlertDtos.ExpiryAlertItemResponse::daysLeft));
        return items;
    }

    private String bucketOf(long daysLeft) {
        if (daysLeft < 0) return EXPIRED;
        if (daysLeft <= 7) return D7;
        if (daysLeft <= 15) return D15;
        if (daysLeft <= 30) return D30;
        return null;
    }

    private String bucketLabel(String bucket) {
        return switch (bucket) {
            case EXPIRED -> "Expired";
            case D7 -> "7 days";
            case D15 -> "15 days";
            case D30 -> "30 days";
            default -> bucket;
        };
    }

    // ==================== DISCOUNT ====================

    @Transactional
    public ExpiryAlertDtos.ExpiryAlertItemResponse giveDiscount(Long shopId, Long batchStockId,
                                                                ExpiryAlertDtos.DiscountRequest request, Long userId) {
        ProductBatchStock stock = findStock(shopId, batchStockId);
        User user = findUser(userId);

        BatchPriceOverride override = batchPriceOverrideRepository.findByBatchStockId(batchStockId)
                .orElseGet(() -> {
                    BatchPriceOverride o = new BatchPriceOverride();
                    o.setShopId(shopId);
                    o.setBatchStock(stock);
                    o.setCreatedBy(user);
                    return o;
                });
        override.setDiscountPct(request.discountPct());
        override.setReason(request.reason());
        override.setCreatedBy(user);
        batchPriceOverrideRepository.save(override);

        logAction(shopId, stock, ExpiryAction.ActionType.DISCOUNT, request.discountPct(), null, null, null,
                request.reason(), user);

        return toItem(shopId, stock, LocalDate.now());
    }

    // ==================== WRITE OFF ====================

    @Transactional
    public ExpiryAlertDtos.ExpiryAlertItemResponse writeOff(Long shopId, Long batchStockId,
                                                             ExpiryAlertDtos.WriteOffRequest request, Long userId) {
        ProductBatchStock stock = findStock(shopId, batchStockId);
        User user = findUser(userId);

        if (request.qty().compareTo(stock.getQtyAvailable()) > 0) {
            throw new DomainException(400, "Write-off qty exceeds available stock: " + stock.getQtyAvailable());
        }

        BigDecimal value = request.qty().multiply(stock.getCostPrice()).setScale(2, RoundingMode.HALF_UP);
        Product product = stock.getProduct();

        inventoryService.writeOffBatch(stock, request.qty(),
                "Expiry write-off: " + product.getName(), userId, LocalDateTime.now());

        lossLedgerService.recordLoss(shopId, LossLedger.LossType.EXPIRY, product.getId(), stock.getBatchId(),
                request.qty(), value, request.note(), LocalDate.now(), userId);

        logAction(shopId, stock, ExpiryAction.ActionType.WRITE_OFF, null, request.qty(), value, null,
                request.note(), user);

        return toItem(shopId, stock, LocalDate.now());
    }

    // ==================== SNOOZE ====================

    @Transactional
    public ExpiryAlertDtos.ExpiryAlertItemResponse snooze(Long shopId, Long batchStockId,
                                                           ExpiryAlertDtos.SnoozeRequest request, Long userId) {
        ProductBatchStock stock = findStock(shopId, batchStockId);
        User user = findUser(userId);
        LocalDate until = LocalDate.now().plusDays(request.days());

        logAction(shopId, stock, ExpiryAction.ActionType.SNOOZE, null, null, null, until, null, user);

        return toItem(shopId, stock, LocalDate.now());
    }

    // ==================== HELPERS ====================

    private ProductBatchStock findStock(Long shopId, Long batchStockId) {
        return batchStockRepository.findById(batchStockId)
                .filter(s -> s.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Stock batch not found"));
    }

    private User findUser(Long userId) {
        return userService.findById(userId).orElseThrow(() -> new DomainException(400, "User not found"));
    }

    private void logAction(Long shopId, ProductBatchStock stock, ExpiryAction.ActionType type,
                            BigDecimal discountPct, BigDecimal writeOffQty, BigDecimal writeOffValue,
                            LocalDate snoozedUntil, String note, User performedBy) {
        ExpiryAction action = new ExpiryAction();
        action.setShopId(shopId);
        action.setBatchStock(stock);
        action.setProductId(stock.getProduct().getId());
        action.setActionType(type);
        action.setDiscountPct(discountPct);
        action.setWriteOffQty(writeOffQty);
        action.setWriteOffValue(writeOffValue);
        action.setSnoozedUntil(snoozedUntil);
        action.setNote(note);
        action.setPerformedBy(performedBy);
        expiryActionRepository.save(action);
    }

    private ExpiryAlertDtos.ExpiryAlertItemResponse toItem(Long shopId, ProductBatchStock stock, LocalDate today) {
        Product product = stock.getProduct();
        LocalDate expiry = stock.getExpiryDate() != null ? stock.getExpiryDate() : stock.getBestBeforeDate();
        long daysLeft = expiry != null ? ChronoUnit.DAYS.between(today, expiry) : 0;
        String bucket = expiry != null ? bucketOf(daysLeft) : null;
        BigDecimal valueAtRisk = stock.getQtyAvailable().multiply(stock.getCostPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountPct = batchPriceOverrideRepository.findByBatchStockId(stock.getId())
                .map(BatchPriceOverride::getDiscountPct).orElse(null);

        return new ExpiryAlertDtos.ExpiryAlertItemResponse(
                stock.getId(), stock.getBatchId(), product.getId(), product.getName(), product.getSku(),
                stock.getQtyAvailable(), product.getUnit().name(), stock.getCostPrice(), valueAtRisk,
                expiry, daysLeft, bucket == null ? "" : bucket, discountPct
        );
    }
}
