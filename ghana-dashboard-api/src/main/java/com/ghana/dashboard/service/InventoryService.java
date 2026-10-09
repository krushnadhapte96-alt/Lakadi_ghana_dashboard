package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.StockMovement;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.InventoryDtos;
import com.ghana.dashboard.dto.InventoryDtos.InsufficientStockException;
import com.ghana.dashboard.repository.ProductBatchStockRepository;
import com.ghana.dashboard.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final ProductBatchStockRepository batchStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserService userService;

    public InventoryService(ProductBatchStockRepository batchStockRepository,
                          StockMovementRepository stockMovementRepository,
                          UserService userService) {
        this.batchStockRepository = batchStockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.userService = userService;
    }

    // ==================== Stock Query Methods ====================

    @Transactional(readOnly = true)
    public BigDecimal getAvailableStock(Long shopId, Long productId) {
        List<ProductBatchStock> batches = batchStockRepository.findByShopIdAndProductId(shopId, productId);
        return batches.stream()
                .map(batch -> batch.getQtyAvailable().add(batch.getQtyReserved()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAvailableStockExcludingReserved(Long shopId, Long productId) {
        List<ProductBatchStock> batches = batchStockRepository.findByShopIdAndProductId(shopId, productId);
        return batches.stream()
                .map(ProductBatchStock::getQtyAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal getReservedStock(Long shopId, Long productId) {
        List<ProductBatchStock> batches = batchStockRepository.findByShopIdAndProductId(shopId, productId);
        return batches.stream()
                .map(ProductBatchStock::getQtyReserved)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<ProductBatchStock> getBatches(Long shopId, Long productId) {
        return batchStockRepository.findByShopIdAndProductIdOrderByReceivedDateAsc(shopId, productId);
    }

    @Transactional(readOnly = true)
    public ProductBatchStock getOldestAvailableBatch(Long shopId, Long productId) {
        List<ProductBatchStock> batches = batchStockRepository.findAvailableBatchesFIFO(shopId, productId);
        return batches.isEmpty() ? null : batches.get(0);
    }

    // ==================== Stock Mutation Methods ====================

    @Transactional
    public ProductBatchStock addStock(Product product, BigDecimal qty, BigDecimal costPrice,
                                       LocalDateTime now, Long userId) {
        return addStock(product, qty, costPrice, null, now, userId);
    }

    @Transactional
    public ProductBatchStock addStock(Product product, BigDecimal qty, BigDecimal costPrice,
                                       java.time.LocalDate bestBeforeDate, LocalDateTime now, Long userId) {
        // Check if this is opening stock (no batches exist)
        List<ProductBatchStock> existingBatches = batchStockRepository.findByShopIdAndProductId(
                product.getShopId(), product.getId());
        
        String batchId = null;
        StockMovement.MovementType movementType;
        
        if (existingBatches.isEmpty()) {
            // This is opening stock
            batchId = "OPENING";
            movementType = StockMovement.MovementType.OPENING;
        } else {
            // Create a new batch
            batchId = generateBatchId();
            movementType = StockMovement.MovementType.BATCH_IN;
        }

        // Create batch stock
        ProductBatchStock batch = new ProductBatchStock();
        batch.setShopId(product.getShopId());
        batch.setProduct(product);
        batch.setBatchId(batchId);
        batch.setQtyAvailable(qty);
        batch.setQtyReserved(BigDecimal.ZERO);
        batch.setCostPrice(costPrice);
        batch.setReceivedDate(java.time.LocalDate.now());
        batch.setBestBeforeDate(bestBeforeDate);
        batch.setActive(true);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batchStockRepository.save(batch);

        // Record movement
        recordMovement(product, batchId, qty, movementType, costPrice, null, userId, now);

        return batch;
    }

    @Transactional
    public ProductBatchStock addStockToBatch(Product product, String batchId, BigDecimal qty,
                                              BigDecimal costPrice, LocalDateTime now, Long userId) {
        ProductBatchStock batch = batchStockRepository.findByShopIdAndProductIdAndBatchId(
                        product.getShopId(), product.getId(), batchId)
                .orElseThrow(() -> new DomainException(404, "Batch not found"));

        // Update batch
        batch.setQtyAvailable(batch.getQtyAvailable().add(qty));
        batch.setCostPrice(costPrice);
        batch.setUpdatedAt(now);
        batchStockRepository.save(batch);

        // Record movement
        recordMovement(product, batchId, qty, StockMovement.MovementType.BATCH_IN, costPrice, null, userId, now);

        return batch;
    }

    @Transactional
    public void reserveStock(Product product, BigDecimal qty, Long userId, LocalDateTime now,
                            String referenceType, Long referenceId) {
        BigDecimal available = getAvailableStockExcludingReserved(product.getShopId(), product.getId());
        
        if (available.compareTo(qty) < 0) {
            throw new InsufficientStockException(product.getId(), product.getSku(), available, qty);
        }

        // Get FIFO batches
        List<ProductBatchStock> batches = batchStockRepository.findAvailableBatchesFIFO(
                product.getShopId(), product.getId());
        
        BigDecimal remaining = qty;
        
        for (ProductBatchStock batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            
            BigDecimal availableInBatch = batch.getQtyAvailable();
            BigDecimal toReserve = remaining.min(availableInBatch);
            
            batch.setQtyReserved(batch.getQtyReserved().add(toReserve));
            batch.setQtyAvailable(batch.getQtyAvailable().subtract(toReserve));
            batch.setUpdatedAt(now);
            batchStockRepository.save(batch);
            
            // Record reserve movement
            recordMovement(product, batch.getBatchId(), toReserve.negate(), 
                    StockMovement.MovementType.ORDER_RESERVE, batch.getCostPrice(), 
                    null, userId, now, referenceType, referenceId);
            
            remaining = remaining.subtract(toReserve);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new InsufficientStockException(product.getId(), product.getSku(), 
                    available.subtract(qty.subtract(remaining)), qty);
        }
    }

    @Transactional
    public void releaseReservation(Product product, BigDecimal qty, Long userId, LocalDateTime now,
                                   String referenceType, Long referenceId) {
        // Get batches with reservations for this product
        List<ProductBatchStock> batches = batchStockRepository.findByShopIdAndProductId(
                product.getShopId(), product.getId());
        
        BigDecimal remaining = qty;
        
        for (ProductBatchStock batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            
            BigDecimal reservedInBatch = batch.getQtyReserved();
            BigDecimal toRelease = remaining.min(reservedInBatch);
            
            batch.setQtyReserved(batch.getQtyReserved().subtract(toRelease));
            batch.setQtyAvailable(batch.getQtyAvailable().add(toRelease));
            batch.setUpdatedAt(now);
            batchStockRepository.save(batch);
            
            // Record release movement
            recordMovement(product, batch.getBatchId(), toRelease,
                    StockMovement.MovementType.ORDER_RELEASE, batch.getCostPrice(),
                    null, userId, now, referenceType, referenceId);
            
            remaining = remaining.subtract(toRelease);
        }
    }

    @Transactional
    public void deductStockFIFO(Product product, BigDecimal qty, Long userId, LocalDateTime now,
                               String reason, String referenceType, Long referenceId) {
        BigDecimal available = getAvailableStockExcludingReserved(product.getShopId(), product.getId());
        
        if (available.compareTo(qty) < 0) {
            throw new InsufficientStockException(product.getId(), product.getSku(), available, qty);
        }

        // Reserve first (to ensure availability)
        reserveStock(product, qty, userId, now, referenceType, referenceId);
        
        // Now deduct from reserved
        List<ProductBatchStock> batches = batchStockRepository.findByShopIdAndProductId(
                product.getShopId(), product.getId());
        
        BigDecimal remaining = qty;
        
        for (ProductBatchStock batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            
            BigDecimal reservedInBatch = batch.getQtyReserved();
            BigDecimal toDeduct = remaining.min(reservedInBatch);
            
            // Deduct from reserved (it was already subtracted from available during reserve)
            batch.setQtyReserved(batch.getQtyReserved().subtract(toDeduct));
            batch.setUpdatedAt(now);
            batchStockRepository.save(batch);
            
            // Record sale movement
            recordMovement(product, batch.getBatchId(), toDeduct.negate(),
                    StockMovement.MovementType.SALE_OUT, batch.getCostPrice(),
                    reason, userId, now, referenceType, referenceId);
            
            remaining = remaining.subtract(toDeduct);
        }
    }

    @Transactional
    public void adjustStock(Product product, String batchId, BigDecimal qty, String reason, 
                           BigDecimal costPrice, Long userId, LocalDateTime now) {
        ProductBatchStock batch = batchId != null 
                ? batchStockRepository.findByShopIdAndProductIdAndBatchId(
                        product.getShopId(), product.getId(), batchId)
                        .orElseThrow(() -> new DomainException(404, "Batch not found"))
                : null;

        StockMovement.MovementType type = qty.signum() > 0 
                ? StockMovement.MovementType.ADJUST_IN 
                : StockMovement.MovementType.ADJUST_OUT;
        
        if (batch != null) {
            BigDecimal newQty = batch.getQtyAvailable().add(qty.abs());
            if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new DomainException(400, "Adjustment would result in negative stock");
            }
            batch.setQtyAvailable(newQty);
            batch.setCostPrice(costPrice);
            batch.setUpdatedAt(now);
            batchStockRepository.save(batch);
        }

        // Record adjustment movement
        recordMovement(product, batchId, qty, type, costPrice, reason, userId, now);
    }

    @Transactional
    public void addOpeningStock(Product product, BigDecimal qty, BigDecimal costPrice,
                                java.time.LocalDate receivedDate, java.time.LocalDate expiryDate,
                                Long userId, LocalDateTime now) {
        // Check if any batches already exist
        List<ProductBatchStock> existing = batchStockRepository.findByShopIdAndProductId(
                product.getShopId(), product.getId());
        
        if (!existing.isEmpty()) {
            throw new DomainException(400, "Opening stock can only be added when no batches exist");
        }

        // Create opening batch
        ProductBatchStock batch = new ProductBatchStock();
        batch.setShopId(product.getShopId());
        batch.setProduct(product);
        batch.setBatchId("OPENING");
        batch.setQtyAvailable(qty);
        batch.setQtyReserved(BigDecimal.ZERO);
        batch.setCostPrice(costPrice);
        batch.setReceivedDate(receivedDate);
        batch.setExpiryDate(expiryDate);
        batch.setActive(true);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batchStockRepository.save(batch);

        // Record opening movement
        recordMovement(product, "OPENING", qty, StockMovement.MovementType.OPENING, costPrice, null, userId, now);
    }

    @Transactional
    public void returnStock(Product product, BigDecimal qty, BigDecimal costPrice, String reason,
                           Long userId, LocalDateTime now) {
        // Return goes to oldest batch (FIFO)
        ProductBatchStock batch = getOldestAvailableBatch(product.getShopId(), product.getId());
        
        if (batch == null) {
            // No batches, create a return batch
            String returnBatchId = "RETURN-" + UUID.randomUUID().toString().substring(0, 8);
            batch = new ProductBatchStock();
            batch.setShopId(product.getShopId());
            batch.setProduct(product);
            batch.setBatchId(returnBatchId);
            batch.setQtyAvailable(BigDecimal.ZERO);
            batch.setQtyReserved(BigDecimal.ZERO);
            batch.setCostPrice(costPrice);
            batch.setReceivedDate(java.time.LocalDate.now());
            batch.setActive(true);
            batch.setCreatedAt(now);
            batch.setUpdatedAt(now);
            batchStockRepository.save(batch);
        }

        batch.setQtyAvailable(batch.getQtyAvailable().add(qty));
        batch.setCostPrice(costPrice);
        batch.setUpdatedAt(now);
        batchStockRepository.save(batch);

        // Record return movement
        recordMovement(product, batch.getBatchId(), qty, StockMovement.MovementType.RETURN_IN, 
                costPrice, reason, userId, now);
    }

    /** Return a returned item's stock to the specific batch it was sold from, when that batch still exists. */
    @Transactional
    public void restockToOriginalBatch(Product product, String batchId, BigDecimal qty, String reason,
                                        Long userId, LocalDateTime now) {
        ProductBatchStock batch = batchId != null
                ? batchStockRepository.findByShopIdAndProductIdAndBatchId(product.getShopId(), product.getId(), batchId)
                        .orElse(null)
                : null;

        if (batch == null) {
            returnStock(product, qty, product.getCostPrice(), reason, userId, now);
            return;
        }

        batch.setQtyAvailable(batch.getQtyAvailable().add(qty));
        batch.setUpdatedAt(now);
        batchStockRepository.save(batch);

        recordMovement(product, batchId, qty, StockMovement.MovementType.RETURN_IN, batch.getCostPrice(), reason, userId, now);
    }

    @Transactional
    public void writeOffBatch(ProductBatchStock batch, BigDecimal qty, String reason, Long userId, LocalDateTime now) {
        if (qty.compareTo(batch.getQtyAvailable()) > 0) {
            throw new DomainException(400, "Write-off qty exceeds available stock in this batch");
        }
        batch.setQtyAvailable(batch.getQtyAvailable().subtract(qty));
        batch.setUpdatedAt(now);
        batchStockRepository.save(batch);

        recordMovement(batch.getProduct(), batch.getBatchId(), qty.negate(),
                StockMovement.MovementType.WRITE_OFF, batch.getCostPrice(), reason, userId, now);
    }

    // ==================== Helper Methods ====================

    private void recordMovement(Product product, String batchId, BigDecimal qty, 
                                StockMovement.MovementType type, BigDecimal unitPrice,
                                String reason, Long userId, LocalDateTime now) {
        recordMovement(product, batchId, qty, type, unitPrice, reason, userId, now, null, null);
    }

    private void recordMovement(Product product, String batchId, BigDecimal qty, 
                                StockMovement.MovementType type, BigDecimal unitPrice,
                                String reason, Long userId, LocalDateTime now,
                                String referenceType, Long referenceId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new DomainException(400, "User not found"));

        BigDecimal totalValue = unitPrice != null 
                ? unitPrice.multiply(qty.abs()).setScale(2, RoundingMode.HALF_UP)
                : null;

        StockMovement movement = new StockMovement();
        movement.setShopId(product.getShopId());
        movement.setProduct(product);
        movement.setBatchId(batchId);
        movement.setChangeQty(qty);
        movement.setType(type);
        movement.setUnitPrice(unitPrice);
        movement.setTotalValue(totalValue);
        movement.setReason(reason);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setUser(user);
        movement.setCreatedAt(now);
        
        stockMovementRepository.save(movement);
    }

    private String generateBatchId() {
        return "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
