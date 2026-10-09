package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.OilType;
import com.ghana.dashboard.domain.PackagingItem;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductBatchStock;
import com.ghana.dashboard.domain.ProductPackaging;
import com.ghana.dashboard.domain.StockMovement;
import com.ghana.dashboard.dto.BatchDtos;
import com.ghana.dashboard.dto.InventoryDtos;
import com.ghana.dashboard.repository.OilTypeRepository;
import com.ghana.dashboard.repository.PackagingItemRepository;
import com.ghana.dashboard.repository.PackagingStockMovementRepository;
import com.ghana.dashboard.repository.ProductBatchStockRepository;
import com.ghana.dashboard.repository.ProductPackagingRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.StockMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final OilTypeRepository oilTypeRepository;
    private final ProductBatchStockRepository batchStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryService inventoryService;
    private final PackagingItemRepository packagingItemRepository;
    private final ProductPackagingRepository productPackagingRepository;
    private final PackagingStockMovementRepository packagingStockMovementRepository;

    public ProductService(ProductRepository productRepository,
                          OilTypeRepository oilTypeRepository,
                          ProductBatchStockRepository batchStockRepository,
                          StockMovementRepository stockMovementRepository,
                          InventoryService inventoryService,
                          PackagingItemRepository packagingItemRepository,
                          ProductPackagingRepository productPackagingRepository,
                          PackagingStockMovementRepository packagingStockMovementRepository) {
        this.productRepository = productRepository;
        this.oilTypeRepository = oilTypeRepository;
        this.batchStockRepository = batchStockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.inventoryService = inventoryService;
        this.packagingItemRepository = packagingItemRepository;
        this.productPackagingRepository = productPackagingRepository;
        this.packagingStockMovementRepository = packagingStockMovementRepository;
    }

    // ==================== Oil Type Methods ====================

@Transactional(readOnly = true)
    public List<InventoryDtos.OilTypeResponse> getAllOilTypes() {
        return oilTypeRepository.findByActiveTrueOrderByNameEnAsc().stream()
                .map(this::toOilTypeResponse).toList();
    }

    /** Settings needs the deactivated rows too, so an oil type can be brought back. */
    @Transactional(readOnly = true)
    public List<InventoryDtos.OilTypeResponse> getAllOilTypesIncludingInactive() {
        return oilTypeRepository.findAllByOrderByNameEnAsc().stream()
                .map(this::toOilTypeResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryDtos.OilTypeResponse getOilType(Long id) {
        return oilTypeRepository.findById(id)
                .map(this::toOilTypeResponse)
                .orElseThrow(() -> new DomainException(404, "Oil type not found"));
    }

    @Transactional
    public InventoryDtos.OilTypeResponse createOilType(InventoryDtos.OilTypeRequest request) {
        if (StringUtils.hasText(request.nameEn()) && oilTypeRepository.existsByNameEn(request.nameEn())) {
            throw new DomainException(409, "Oil type with this English name already exists");
        }

        OilType oilType = new OilType();
        oilType.setNameEn(request.nameEn());
        oilType.setNameHi(request.nameHi());
        oilType.setDensity(request.density());
        oilType.setBestBeforeDays(request.bestBeforeDays());
        oilType.setColourTag(request.colourTag());
        oilType.setActive(true);
        oilType.setCreatedAt(LocalDateTime.now());
        oilType.setUpdatedAt(LocalDateTime.now());

        oilTypeRepository.save(oilType);
        return toOilTypeResponse(oilType);
    }

    @Transactional
    public InventoryDtos.OilTypeResponse updateOilType(Long id, InventoryDtos.OilTypeRequest request) {
        OilType oilType = oilTypeRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Oil type not found"));

        if (StringUtils.hasText(request.nameEn()) && !request.nameEn().equals(oilType.getNameEn()) 
                && oilTypeRepository.existsByNameEn(request.nameEn())) {
            throw new DomainException(409, "Oil type with this English name already exists");
        }

        oilType.setNameEn(request.nameEn());
        oilType.setNameHi(request.nameHi());
        oilType.setDensity(request.density());
        oilType.setBestBeforeDays(request.bestBeforeDays());
        oilType.setColourTag(request.colourTag());
        if (request.active() != null) {
            oilType.setActive(request.active());
        }
        oilType.setUpdatedAt(LocalDateTime.now());

        oilTypeRepository.save(oilType);
        return toOilTypeResponse(oilType);
    }

    @Transactional
    public void deleteOilType(Long id) {
        OilType oilType = oilTypeRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Oil type not found"));
        oilType.setActive(false);
        oilType.setUpdatedAt(LocalDateTime.now());
        oilTypeRepository.save(oilType);
    }

    // ==================== Product Methods ====================

    @Transactional(readOnly = true)
    public List<InventoryDtos.ProductResponse> getAllProducts(Long shopId) {
        return productRepository.findByShopIdOrderByNameAsc(shopId).stream()
                .map(this::toProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryDtos.ProductSummaryResponse> getProductSummaries(Long shopId, boolean isKhali) {
        List<Product> products = isKhali
                ? productRepository.findByShopIdAndIsKhaliTrueAndActiveTrueOrderByNameAsc(shopId)
                : productRepository.findByShopIdAndIsKhaliFalseAndActiveTrueOrderByNameAsc(shopId);
        return products.stream()
                .map(this::toProductSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryDtos.ProductResponse getProduct(Long shopId, Long id) {
        return productRepository.findById(id)
                .filter(p -> p.getShopId().equals(shopId))
                .map(this::toProductResponse)
                .orElseThrow(() -> new DomainException(404, "Product not found"));
    }

    @Transactional
    public InventoryDtos.ProductResponse createProduct(Long shopId, InventoryDtos.ProductRequest request) {
        if (request.oilTypeId() != null) {
            OilType oilType = oilTypeRepository.findById(request.oilTypeId())
                    .orElseThrow(() -> new DomainException(400, "Oil type not found"));
        }

        // Generate SKU
        String sku = generateSku(shopId);

        Product product = new Product();
        product.setShopId(shopId);
        if (request.oilTypeId() != null) {
            product.setOilType(oilTypeRepository.findById(request.oilTypeId()).orElse(null));
        }
        product.setName(request.name());
        product.setSku(sku);
        product.setPackSize(request.packSize());
        product.setUnit(Product.Unit.valueOf(request.unit()));
        product.setContainer(Product.Container.valueOf(request.container()));
        product.setSellingPrice(request.sellingPrice());
        product.setMrp(request.mrp());
        product.setCostPrice(request.costPrice());
        product.setLowStockThreshold(request.lowStockThreshold());
        product.setActive(true);
        product.setKhali(request.isKhali());
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        productRepository.save(product);
        return toProductResponse(product);
    }

    @Transactional
    public InventoryDtos.ProductResponse updateProduct(Long shopId, Long id, InventoryDtos.ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new DomainException(404, "Product not found"));

        if (!product.getShopId().equals(shopId)) {
            throw new DomainException(403, "Product does not belong to this shop");
        }

        if (request.oilTypeId() != null) {
            OilType oilType = oilTypeRepository.findById(request.oilTypeId())
                    .orElseThrow(() -> new DomainException(400, "Oil type not found"));
            product.setOilType(oilType);
        }

        product.setName(request.name());
        product.setPackSize(request.packSize());
        product.setUnit(Product.Unit.valueOf(request.unit()));
        product.setContainer(Product.Container.valueOf(request.container()));
        product.setSellingPrice(request.sellingPrice());
        product.setMrp(request.mrp());
        product.setCostPrice(request.costPrice());
        product.setLowStockThreshold(request.lowStockThreshold());
        product.setKhali(request.isKhali());
        product.setUpdatedAt(LocalDateTime.now());

        productRepository.save(product);
        return toProductResponse(product);
    }

    @Transactional
    public void updateProductPrice(Long shopId, Long productId, InventoryDtos.UpdateProductPriceRequest request) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Product not found"));

        product.setSellingPrice(request.sellingPrice());
        product.setMrp(request.mrp());
        product.setCostPrice(request.costPrice());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    @Transactional
    public void updateProductThreshold(Long shopId, Long productId, InventoryDtos.UpdateProductThresholdRequest request) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Product not found"));

        product.setLowStockThreshold(request.lowStockThreshold());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long shopId, Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Product not found"));
        product.setActive(false);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    // ==================== Inventory Methods ====================

    @Transactional(readOnly = true)
    public InventoryDtos.AggregatedInventoryResponse getAggregatedInventory(Long shopId) {
        List<Product> oilProducts = productRepository.findByShopIdAndIsKhaliFalseAndActiveTrueOrderByNameAsc(shopId);
        List<Product> khaliProducts = productRepository.findByShopIdAndIsKhaliTrueAndActiveTrueOrderByNameAsc(shopId);
        List<PackagingItem> packagingItems = packagingItemRepository.findByShopIdAndActiveTrueOrderByNameAsc(shopId);

        List<InventoryDtos.InventoryItemResponse> oilProductResponses = oilProducts.stream()
                .map(p -> toInventoryItemResponse(p, shopId))
                .collect(Collectors.toList());

        List<InventoryDtos.InventoryItemResponse> khaliProductResponses = khaliProducts.stream()
                .map(p -> toInventoryItemResponse(p, shopId))
                .collect(Collectors.toList());

        List<InventoryDtos.PackagingItemResponse> packagingResponses = packagingItems.stream()
                .map(this::toPackagingItemResponse)
                .collect(Collectors.toList());

        return new InventoryDtos.AggregatedInventoryResponse(
                oilProductResponses, khaliProductResponses, packagingResponses);
    }

    @Transactional(readOnly = true)
    public List<InventoryDtos.BatchStockResponse> getProductBatches(Long shopId, Long productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Product not found"));

        return inventoryService.getBatches(shopId, productId).stream()
                .map(batch -> toBatchStockResponse(batch, product))
                .collect(Collectors.toList());
    }

    @Transactional
    public void addOpeningStock(Long shopId, Long productId, InventoryDtos.OpeningStockRequest request, Long userId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Product not found"));

        inventoryService.addOpeningStock(
                product, request.qty(), request.costPrice(), request.receivedDate(), request.expiryDate(),
                userId, LocalDateTime.now());
    }

    @Transactional
    public void adjustStock(Long shopId, Long productId, InventoryDtos.StockAdjustmentRequest request, Long userId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Product not found"));

        // Get FIFO batches
        List<ProductBatchStock> batches = inventoryService.getBatches(shopId, productId);

        if (batches.isEmpty()) {
            // No batches exist, treat as opening stock adjustment
            if (request.qty().compareTo(BigDecimal.ZERO) > 0) {
                inventoryService.addStock(product, request.qty(), product.getCostPrice(),
                        LocalDateTime.now(), userId);
            } else {
                throw new DomainException(400, "Cannot adjust negative stock when no batches exist");
            }
        } else {
            // Adjust the oldest batch
            inventoryService.adjustStock(product, batches.get(0).getBatchId(), request.qty(),
                    request.reason(), request.qty().signum() > 0 ? request.qty() : product.getCostPrice(),
                    userId, LocalDateTime.now());
        }
    }

    // ==================== Stock Movement Methods ====================

    @Transactional(readOnly = true)
    public Page<InventoryDtos.StockMovementResponse> getStockMovements(Long shopId,
            InventoryDtos.StockMovementFilter filter, Pageable pageable) {
        // This is a simplified version - in production you'd use JPA Specifications or QueryDSL
        List<StockMovement> movements = stockMovementRepository.findByShopIdOrderByCreatedAtDesc(shopId);
        
        List<InventoryDtos.StockMovementResponse> responses = movements.stream()
                .filter(m -> {
                    if (filter.productId() != null && !m.getProduct().getId().equals(filter.productId())) {
                        return false;
                    }
                    if (filter.type() != null && !m.getType().name().equals(filter.type())) {
                        return false;
                    }
                    return true;
                })
                .map(this::toStockMovementResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, responses.size());
    }

    // ==================== Packaging Mapping ====================

    @Transactional(readOnly = true)
    public List<BatchDtos.PackagingMappingResponse> getPackagingMappings(Long shopId, Long productId) {
        return productPackagingRepository.findByShopIdAndProductId(shopId, productId).stream()
                .map(pp -> toPackagingMappingResponse(pp, shopId))
                .collect(Collectors.toList());
    }

    @Transactional
    public BatchDtos.PackagingMappingResponse addPackagingMapping(Long shopId, Long productId,
                                                                               BatchDtos.PackagingMappingRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Product not found"));
        PackagingItem packagingItem = packagingItemRepository.findById(request.packagingItemId())
                .orElseThrow(() -> new DomainException(404, "Packaging item not found"));

        if (productPackagingRepository.existsByShopIdAndProductIdAndPackagingItemId(
                shopId, productId, request.packagingItemId())) {
            throw new DomainException(409, "Packaging mapping already exists for this product");
        }

        ProductPackaging pp = new ProductPackaging();
        pp.setShopId(shopId);
        pp.setProduct(product);
        pp.setPackagingItem(packagingItem);
        pp.setUnitsPerPack(request.unitsPerPack());
        pp.setActive(true);
        pp.setCreatedAt(LocalDateTime.now());
        pp.setUpdatedAt(LocalDateTime.now());
        productPackagingRepository.save(pp);

        return toPackagingMappingResponse(pp, shopId);
    }

    @Transactional
    public void removePackagingMapping(Long shopId, Long mappingId) {
        ProductPackaging pp = productPackagingRepository.findById(mappingId)
                .orElseThrow(() -> new DomainException(404, "Packaging mapping not found"));
        if (!pp.getShopId().equals(shopId)) {
            throw new DomainException(403, "Mapping does not belong to this shop");
        }
        productPackagingRepository.delete(pp);
    }

    @Transactional
    public BatchDtos.PackagingMappingResponse updatePackagingMapping(Long shopId, Long mappingId,
                                                                                 BatchDtos.PackagingMappingRequest request) {
        ProductPackaging pp = productPackagingRepository.findById(mappingId)
                .orElseThrow(() -> new DomainException(404, "Packaging mapping not found"));
        if (!pp.getShopId().equals(shopId)) {
            throw new DomainException(403, "Mapping does not belong to this shop");
        }
        pp.setUnitsPerPack(request.unitsPerPack());
        pp.setUpdatedAt(LocalDateTime.now());
        productPackagingRepository.save(pp);
        return toPackagingMappingResponse(pp, shopId);
    }

    // ==================== Helper Methods ====================

    private BatchDtos.PackagingMappingResponse toPackagingMappingResponse(ProductPackaging pp, Long shopId) {
        return new BatchDtos.PackagingMappingResponse(
                pp.getId(),
                pp.getProduct().getId(),
                pp.getProduct().getName(),
                pp.getPackagingItem().getId(),
                pp.getPackagingItem().getName(),
                pp.getPackagingItem().getType().name(),
                pp.getPackagingItem().getSize(),
                pp.getUnitsPerPack(),
                pp.isActive()
        );
    }

    private String generateSku(Long shopId) {
        String shopPrefix = "SH" + shopId;
        long count = productRepository.countByShopId(shopId) + 1;
        return shopPrefix + String.format("-%05d", count);
    }

    private InventoryDtos.OilTypeResponse toOilTypeResponse(OilType oilType) {
        return new InventoryDtos.OilTypeResponse(
                oilType.getId(),
                oilType.getNameEn(),
                oilType.getNameHi(),
                oilType.getDensity(),
                oilType.getBestBeforeDays(),
                oilType.getColourTag(),
                oilType.isActive(),
                oilType.getCreatedAt() != null ? oilType.getCreatedAt().toString() : null
        );
    }

    private InventoryDtos.ProductResponse toProductResponse(Product product) {
        return new InventoryDtos.ProductResponse(
                product.getId(),
                product.getShopId(),
                product.getOilType() != null ? product.getOilType().getId() : null,
                product.getOilType() != null ? product.getOilType().getNameEn() : null,
                product.getName(),
                product.getSku(),
                product.getPackSize(),
                product.getUnit().name(),
                product.getContainer().name(),
                product.getSellingPrice(),
                product.getMrp(),
                product.getCostPrice(),
                product.getLowStockThreshold(),
                product.isActive(),
                product.isKhali(),
                product.getCreatedAt() != null ? product.getCreatedAt().toString() : null
        );
    }

    private InventoryDtos.ProductSummaryResponse toProductSummaryResponse(Product product) {
        return new InventoryDtos.ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getOilType() != null ? product.getOilType().getNameEn() : null,
                product.getPackSize(),
                product.getUnit().name(),
                product.getContainer().name(),
                product.isKhali()
        );
    }

    private InventoryDtos.InventoryItemResponse toInventoryItemResponse(Product product, Long shopId) {
        BigDecimal available = inventoryService.getAvailableStock(shopId, product.getId());
        BigDecimal reserved = inventoryService.getReservedStock(shopId, product.getId());

        return new InventoryDtos.InventoryItemResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getOilType() != null ? product.getOilType().getNameEn() : null,
                product.getPackSize(),
                product.getUnit().name(),
                product.getContainer().name(),
                product.isKhali(),
                available,
                reserved,
                product.getSellingPrice(),
                product.getCostPrice(),
                product.getLowStockThreshold(),
                available.compareTo(product.getLowStockThreshold()) < 0,
                product.isActive()
        );
    }

    private InventoryDtos.BatchStockResponse toBatchStockResponse(ProductBatchStock batch, Product product) {
        return new InventoryDtos.BatchStockResponse(
                batch.getId(),
                batch.getBatchId(),
                batch.getQtyAvailable(),
                batch.getQtyReserved(),
                batch.getCostPrice(),
                batch.getReceivedDate(),
                batch.getExpiryDate(),
                batch.getBestBeforeDate(),
                batch.isActive(),
                batch.isLowStock()
        );
    }

    private InventoryDtos.StockMovementResponse toStockMovementResponse(StockMovement movement) {
        return new InventoryDtos.StockMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getProduct().getName(),
                movement.getBatchId(),
                movement.getChangeQty(),
                movement.getType().name(),
                movement.getUnitPrice(),
                movement.getTotalValue(),
                movement.getReason(),
                movement.getNote(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getUser().getId(),
                movement.getUser().getName(),
                movement.getCreatedAt() != null ? movement.getCreatedAt().toString() : null
        );
    }

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
}
