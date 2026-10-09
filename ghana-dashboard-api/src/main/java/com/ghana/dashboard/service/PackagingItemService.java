package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.PackagingItem;
import com.ghana.dashboard.domain.PackagingStockMovement;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.InventoryDtos;
import com.ghana.dashboard.repository.PackagingItemRepository;
import com.ghana.dashboard.repository.PackagingStockMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PackagingItemService {

    private final PackagingItemRepository packagingItemRepository;
    private final PackagingStockMovementRepository packagingStockMovementRepository;
    private final UserService userService;

    public PackagingItemService(PackagingItemRepository packagingItemRepository,
                                 PackagingStockMovementRepository packagingStockMovementRepository,
                                 UserService userService) {
        this.packagingItemRepository = packagingItemRepository;
        this.packagingStockMovementRepository = packagingStockMovementRepository;
        this.userService = userService;
    }

    // ==================== CRUD Methods ====================

    @Transactional(readOnly = true)
    public List<InventoryDtos.PackagingItemResponse> getAllPackagingItems(Long shopId) {
        return packagingItemRepository.findByShopIdOrderByNameAsc(shopId).stream()
                .map(this::toPackagingItemResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryDtos.PackagingItemResponse> getAllActivePackagingItems(Long shopId) {
        return packagingItemRepository.findByShopIdAndActiveTrueOrderByNameAsc(shopId).stream()
                .map(this::toPackagingItemResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryDtos.PackagingItemResponse getPackagingItem(Long shopId, Long id) {
        return packagingItemRepository.findById(id)
                .filter(item -> item.getShopId().equals(shopId))
                .map(this::toPackagingItemResponse)
                .orElseThrow(() -> new DomainException(404, "Packaging item not found"));
    }

    private static PackagingItem.PackagingType parseType(String raw) {
        try {
            return PackagingItem.PackagingType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new DomainException(400, "Invalid packaging type: " + raw
                    + " (allowed: bottle, cap, label, tin, bag, carton)");
        }
    }

    @Transactional
    public InventoryDtos.PackagingItemResponse createPackagingItem(Long shopId, 
                                                                      InventoryDtos.PackagingItemRequest request) {
        PackagingItem.PackagingType type = parseType(request.type());
        // Check if item with same name and type already exists for this shop
        if (packagingItemRepository.existsByShopIdAndNameAndType(shopId, request.name(), type)) {
            throw new DomainException(409, "Packaging item with this name and type already exists");
        }

        PackagingItem item = new PackagingItem();
        item.setShopId(shopId);
        item.setName(request.name());
        item.setType(type);
        item.setSize(request.size());
        item.setStockQty(request.stockQty());
        item.setCostPerUnit(request.costPerUnit());
        item.setLowThreshold(request.lowThreshold());
        item.setActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        packagingItemRepository.save(item);
        return toPackagingItemResponse(item);
    }

    @Transactional
    public InventoryDtos.PackagingItemResponse updatePackagingItem(Long shopId, Long id,
                                                                      InventoryDtos.PackagingItemRequest request) {
        PackagingItem item = packagingItemRepository.findById(id)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Packaging item not found"));
        PackagingItem.PackagingType type = parseType(request.type());

        // Check if name+type combination already exists for another item
        if (packagingItemRepository.existsByShopIdAndNameAndType(shopId, request.name(), type)) {
            PackagingItem existing = packagingItemRepository.findByShopIdAndNameAndType(shopId, request.name(), type)
                    .orElse(null);
            if (existing != null && !existing.getId().equals(id)) {
                throw new DomainException(409, "Packaging item with this name and type already exists");
            }
        }

        item.setName(request.name());
        item.setType(type);
        item.setSize(request.size());
        item.setCostPerUnit(request.costPerUnit());
        item.setLowThreshold(request.lowThreshold());
        item.setUpdatedAt(LocalDateTime.now());

        packagingItemRepository.save(item);
        return toPackagingItemResponse(item);
    }

    @Transactional
    public void deletePackagingItem(Long shopId, Long id) {
        PackagingItem item = packagingItemRepository.findById(id)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Packaging item not found"));
        item.setActive(false);
        item.setUpdatedAt(LocalDateTime.now());
        packagingItemRepository.save(item);
    }

    // ==================== Stock Methods ====================

    @Transactional
    public void recordPurchase(Long shopId, Long itemId, 
                               InventoryDtos.PackagingPurchaseRequest request, Long userId) {
        PackagingItem item = packagingItemRepository.findById(itemId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Packaging item not found"));

        User user = userService.findById(userId)
                .orElseThrow(() -> new DomainException(400, "User not found"));

        LocalDateTime now = LocalDateTime.now();
        BigDecimal qty = request.qty();
        BigDecimal costPerUnit = request.costPerUnit();
        BigDecimal totalCost = costPerUnit.multiply(qty).setScale(2, RoundingMode.HALF_UP);

        // Update item stock
        item.setStockQty(item.getStockQty().add(qty));
        item.setCostPerUnit(costPerUnit);
        item.setUpdatedAt(now);
        packagingItemRepository.save(item);

        // Record movement
        PackagingStockMovement movement = new PackagingStockMovement();
        movement.setShopId(shopId);
        movement.setItem(item);
        movement.setChangeQty(qty);
        movement.setType(PackagingStockMovement.PackagingMovementType.PURCHASE);
        movement.setUnitCost(costPerUnit);
        movement.setTotalCost(totalCost);
        movement.setNote(request.note());
        movement.setUser(user);
        movement.setCreatedAt(now);

        packagingStockMovementRepository.save(movement);
    }

    @Transactional
    public void adjustStock(Long shopId, Long itemId, 
                           InventoryDtos.PackagingStockAdjustmentRequest request, Long userId) {
        PackagingItem item = packagingItemRepository.findById(itemId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Packaging item not found"));

        User user = userService.findById(userId)
                .orElseThrow(() -> new DomainException(400, "User not found"));

        LocalDateTime now = LocalDateTime.now();
        BigDecimal qty = request.qty();
        BigDecimal currentStock = item.getStockQty();
        BigDecimal newStock = currentStock.add(qty);

        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(400, "Adjustment would result in negative stock. Current: " + currentStock + ", Adjustment: " + qty);
        }

        // Update item stock
        item.setStockQty(newStock);
        BigDecimal costPerUnit = qty.signum() > 0 ? request.qty() : item.getCostPerUnit();
        // Don't update cost per unit on negative adjustments
        if (qty.compareTo(BigDecimal.ZERO) > 0) {
            item.setCostPerUnit(costPerUnit);
        }
        item.setUpdatedAt(now);
        packagingItemRepository.save(item);

        // Record movement
        PackagingStockMovement.PackagingMovementType type = qty.signum() > 0 
                ? PackagingStockMovement.PackagingMovementType.ADJUST_IN 
                : PackagingStockMovement.PackagingMovementType.ADJUST_OUT;

        PackagingStockMovement movement = new PackagingStockMovement();
        movement.setShopId(shopId);
        movement.setItem(item);
        movement.setChangeQty(qty);
        movement.setType(type);
        movement.setUnitCost(item.getCostPerUnit());
        movement.setTotalCost(item.getCostPerUnit().multiply(qty.abs()).setScale(2, RoundingMode.HALF_UP));
        movement.setReason(request.reason());
        movement.setNote(request.note());
        movement.setUser(user);
        movement.setCreatedAt(now);

        packagingStockMovementRepository.save(movement);
    }

    @Transactional
    public void consumeStock(Long shopId, Long itemId, BigDecimal qty, String reason,
                            Long userId, String referenceType, Long referenceId) {
        PackagingItem item = packagingItemRepository.findById(itemId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Packaging item not found"));

        User user = userService.findById(userId)
                .orElseThrow(() -> new DomainException(400, "User not found"));

        LocalDateTime now = LocalDateTime.now();
        BigDecimal currentStock = item.getStockQty();

        if (currentStock.compareTo(qty) < 0) {
            throw new DomainException(400, "Insufficient stock for packaging item. Available: " + currentStock + ", Requested: " + qty);
        }

        // Update item stock
        item.setStockQty(currentStock.subtract(qty));
        item.setUpdatedAt(now);
        packagingItemRepository.save(item);

        // Record movement
        PackagingStockMovement movement = new PackagingStockMovement();
        movement.setShopId(shopId);
        movement.setItem(item);
        movement.setChangeQty(qty.negate());
        movement.setType(PackagingStockMovement.PackagingMovementType.CONSUME);
        movement.setUnitCost(item.getCostPerUnit());
        movement.setTotalCost(item.getCostPerUnit().multiply(qty).setScale(2, RoundingMode.HALF_UP));
        movement.setReason(reason);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setUser(user);
        movement.setCreatedAt(now);

        packagingStockMovementRepository.save(movement);
    }

    @Transactional(readOnly = true)
    public Page<InventoryDtos.PackagingMovementResponse> getPackagingMovements(Long shopId,
            InventoryDtos.PackagingMovementFilter filter, Pageable pageable) {
        List<PackagingStockMovement> movements;

        if (filter.itemId() != null) {
            movements = packagingStockMovementRepository.findByItemIdOrderByCreatedAtDesc(filter.itemId());
        } else {
            movements = packagingStockMovementRepository.findByShopIdOrderByCreatedAtDesc(shopId);
        }

        List<InventoryDtos.PackagingMovementResponse> responses = movements.stream()
                .filter(m -> {
                    if (filter.type() != null && !m.getType().name().equals(filter.type())) {
                        return false;
                    }
                    return true;
                })
                .map(this::toPackagingMovementResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, responses.size());
    }

    // ==================== Helper Methods ====================

    private InventoryDtos.PackagingItemResponse toPackagingItemResponse(PackagingItem item) {
        BigDecimal lowThreshold = item.getLowThreshold();
        boolean lowStock = item.getStockQty().compareTo(lowThreshold) < 0;

        return new InventoryDtos.PackagingItemResponse(
                item.getId(),
                item.getShopId(),
                item.getName(),
                item.getType().name(),
                item.getSize(),
                item.getStockQty(),
                item.getCostPerUnit(),
                item.getLowThreshold(),
                item.isActive(),
                lowStock,
                item.getCreatedAt() != null ? item.getCreatedAt().toString() : null
        );
    }

    private InventoryDtos.PackagingMovementResponse toPackagingMovementResponse(PackagingStockMovement movement) {
        return new InventoryDtos.PackagingMovementResponse(
                movement.getId(),
                movement.getItem().getId(),
                movement.getItem().getName(),
                movement.getItem().getType().name(),
                movement.getChangeQty(),
                movement.getType().name(),
                movement.getUnitCost(),
                movement.getTotalCost(),
                movement.getReason(),
                movement.getNote(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getUser().getId(),
                movement.getUser().getName(),
                movement.getCreatedAt() != null ? movement.getCreatedAt().toString() : null
        );
    }
}
