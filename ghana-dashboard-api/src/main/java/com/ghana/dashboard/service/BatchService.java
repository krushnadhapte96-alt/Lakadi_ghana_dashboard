package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.*;
import com.ghana.dashboard.dto.BatchDtos;
import com.ghana.dashboard.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BatchService {

    private static final String BATCH_CODE_PREFIX = "B-";
    private static final String SEED_CONSUMPTION_REFERENCE = "BATCH_SEED_CONSUME";
    private static final String BOTTLE_STOCK_REFERENCE = "BATCH_BOTTLE";
    private static final String BATCH_SEED_RETURN_REFERENCE = "BATCH_SEED_RETURN";

    private final BatchRepository batchRepository;
    private final BatchPackagingUsageRepository batchPackagingUsageRepository;
    private final ProductPackagingRepository productPackagingRepository;
    private final BatchQualityNoteRepository batchQualityNoteRepository;
    private final SeedStockRepository seedStockRepository;
    private final SeedStockMovementRepository seedStockMovementRepository;
    private final SeedTypeRepository seedTypeRepository;
    private final OilTypeRepository oilTypeRepository;
    private final ProductRepository productRepository;
    private final ProductBatchStockRepository productBatchStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryService inventoryService;
    private final PackagingItemRepository packagingItemRepository;
    private final PackagingStockMovementRepository packagingStockMovementRepository;
    private final UserService userService;
    private final CostingService costingService;
    private final DashboardCache dashboardCache;

    public BatchService(BatchRepository batchRepository,
                        BatchPackagingUsageRepository batchPackagingUsageRepository,
                        ProductPackagingRepository productPackagingRepository,
                        BatchQualityNoteRepository batchQualityNoteRepository,
                        SeedStockRepository seedStockRepository,
                        SeedStockMovementRepository seedStockMovementRepository,
                        SeedTypeRepository seedTypeRepository,
                        OilTypeRepository oilTypeRepository,
                        ProductRepository productRepository,
                        ProductBatchStockRepository productBatchStockRepository,
                        StockMovementRepository stockMovementRepository,
                        InventoryService inventoryService,
                        PackagingItemRepository packagingItemRepository,
                        PackagingStockMovementRepository packagingStockMovementRepository,
                        UserService userService,
                        CostingService costingService,
                        DashboardCache dashboardCache) {
        this.batchRepository = batchRepository;
        this.batchPackagingUsageRepository = batchPackagingUsageRepository;
        this.productPackagingRepository = productPackagingRepository;
        this.batchQualityNoteRepository = batchQualityNoteRepository;
        this.seedStockRepository = seedStockRepository;
        this.seedStockMovementRepository = seedStockMovementRepository;
        this.seedTypeRepository = seedTypeRepository;
        this.oilTypeRepository = oilTypeRepository;
        this.productRepository = productRepository;
        this.productBatchStockRepository = productBatchStockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.inventoryService = inventoryService;
        this.packagingItemRepository = packagingItemRepository;
        this.packagingStockMovementRepository = packagingStockMovementRepository;
        this.userService = userService;
        this.costingService = costingService;
        this.dashboardCache = dashboardCache;
    }

    // ==================== CREATE BATCH ====================

    @Transactional
    public BatchDtos.CreateBatchResponse createBatch(Long shopId, BatchDtos.CreateBatchRequest request, Long userId) {
        OilType oilType = oilTypeRepository.findById(request.oilTypeId())
                .orElseThrow(() -> new DomainException(404, "Oil type not found"));
        SeedType seedType = seedTypeRepository.findById(request.seedTypeId())
                .orElseThrow(() -> new DomainException(404, "Seed type not found"));

        SeedStock seedStock = seedStockRepository.findByShopIdAndSeedTypeId(shopId, request.seedTypeId()).orElse(null);
        if (seedStock == null || seedStock.getAvailableKg().compareTo(request.seedKg()) < 0) {
            throw new DomainException(400, "Insufficient seed stock. Available: " +
                    (seedStock != null ? seedStock.getAvailableKg() : BigDecimal.ZERO) +
                    " kg, requested: " + request.seedKg() + " kg");
        }

        BigDecimal previousSeedQty = seedStock.getAvailableKg();
        seedStock.setAvailableKg(previousSeedQty.subtract(request.seedKg()));
        seedStockRepository.save(seedStock);

        User user = userService.findById(userId).orElseThrow(() -> new DomainException(400, "User not found"));
        recordSeedMovement(shopId, seedType, request.seedKg().negate(),
                SeedStockMovement.MovementType.BATCH_OUT, seedStock.getAvgRate(),
                "Batch creation - seed consumption", null, userId,
                SEED_CONSUMPTION_REFERENCE, null);

        LocalDate bestBeforeDate = request.pressStart().toLocalDate()
                .plusDays(oilType.getBestBeforeDays().longValue());
        String code = generateBatchCode(shopId);

        Batch batch = new Batch();
        batch.setShopId(shopId);
        batch.setCode(code);
        batch.setOilType(oilType);
        batch.setSeedType(seedType);
        batch.setSeedKg(request.seedKg());
        batch.setSeedCost(request.seedKg().multiply(seedStock.getAvgRate()).setScale(2, RoundingMode.HALF_UP));
        batch.setPressStart(request.pressStart());
        batch.setOilLitres(BigDecimal.ZERO);
        batch.setKhaliKg(BigDecimal.ZERO);
        batch.setYieldPct(BigDecimal.ZERO);
        batch.setMfgDate(request.pressStart().toLocalDate());
        batch.setBestBeforeDate(bestBeforeDate);
        batch.setStatus(Batch.Status.PRESSING.name());
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);

        dashboardCache.evict(shopId);
        return toCreateBatchResponse(batch);
    }

    // ==================== RECORD OUTPUT ====================

    @Transactional
    public BatchDtos.RecordOutputResponse recordOutput(Long shopId, Long batchId,
                                                        BatchDtos.RecordOutputRequest request, Long userId) {
        Batch batch = findBatch(batchId);
        verifyStatus(batch, Batch.Status.PRESSING);

        if (request.oilLitres().compareTo(BigDecimal.ZERO) <= 0 && request.khaliKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException(400, "Must record at least some oil or khali output");
        }

        BigDecimal totalOutputSoFar = batch.getOilLitres().add(batch.getKhaliKg());
        BigDecimal newTotalOutput = totalOutputSoFar.add(request.oilLitres()).add(request.khaliKg());
        if (newTotalOutput.compareTo(batch.getSeedKg()) > 0) {
            throw new DomainException(400, "Total output exceeds seed input");
        }

        batch.setOilLitres(batch.getOilLitres().add(request.oilLitres()));
        batch.setKhaliKg(batch.getKhaliKg().add(request.khaliKg()));

        if (batch.getSeedKg().compareTo(BigDecimal.ZERO) > 0) {
            batch.setYieldPct(batch.getOilLitres().divide(batch.getSeedKg(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        }

        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        costingService.autoComputeBatchCost(shopId, batch.getId());

        Product oilProduct = findOilProductForOilType(shopId, batch.getOilType().getId());
        if (oilProduct != null) {
            inventoryService.addStock(oilProduct, request.oilLitres(),
                    costingService.bottledUnitCost(shopId, batch, oilProduct),
                    batch.getBestBeforeDate(), LocalDateTime.now(), userId);
            recordStockMovement(oilProduct, request.oilLitres(),
                    StockMovement.MovementType.BATCH_OUTPUT, "Batch output: " + batch.getCode(), userId);
        }

        Product khaliProduct = findKhaliProduct(shopId, batch.getOilType().getId());
        if (khaliProduct != null) {
            inventoryService.addStock(khaliProduct, request.khaliKg(),
                    costingService.bottledUnitCost(shopId, batch, khaliProduct),
                    batch.getBestBeforeDate(), LocalDateTime.now(), userId);
            recordStockMovement(khaliProduct, request.khaliKg(),
                    StockMovement.MovementType.BATCH_OUTPUT, "Batch khali: " + batch.getCode(), userId);
        }

        dashboardCache.evict(shopId);
        return toRecordOutputResponse(batch);
    }

    // ==================== MARK READY ====================

    @Transactional
    public BatchDtos.MarkReadyResponse markReady(Long shopId, Long batchId,
                                                  BatchDtos.MarkReadyRequest request, Long userId) {
        Batch batch = findBatch(batchId);
        verifyStatus(batch, Batch.Status.PRESSING);
        if (batch.getOilLitres().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException(400, "Cannot mark ready: no oil output recorded");
        }
        batch.setSettlingStart(LocalDateTime.now());
        batch.setReadyDate(request.readyDate());
        batch.setStatus(Batch.Status.SETTLING.name());
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        return new BatchDtos.MarkReadyResponse(batch.getId(), batch.getCode(), request.readyDate(), batch.getStatus());
    }

    // ==================== BOTTLE BATCH ====================

    @Transactional
    public BatchDtos.BottleBatchResponse bottleBatch(Long shopId, Long batchId,
                                                      BatchDtos.BottleBatchRequest request, Long userId) {
        Batch batch = findBatch(batchId);
        verifyStatus(batch, Batch.Status.SETTLING);

        if (request.totalLitresUsed().compareTo(batch.getOilLitres()) > 0) {
            throw new DomainException(400, "Total litres used exceeds available oil");
        }

        costingService.autoComputeBatchCost(shopId, batch.getId());

        List<BatchDtos.BottleItemResult> results = new ArrayList<>();
        boolean allSuccess = true;

        for (BatchDtos.BottleItem item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .filter(p -> p.getShopId().equals(shopId))
                    .orElseThrow(() -> new DomainException(404, "Product not found"));

            List<ProductPackaging> mappings = productPackagingRepository.findByShopIdAndProductId(shopId, item.productId());
            if (mappings.isEmpty()) {
                results.add(new BatchDtos.BottleItemResult(
                        item.productId(), product.getName(), item.units(),
                        BigDecimal.ZERO, false, "No packaging mapping configured"
                ));
                allSuccess = false;
                continue;
            }

            ProductPackaging mapping = mappings.get(0);
            PackagingItem packagingItem = mapping.getPackagingItem();
            BigDecimal unitsPerPack = mapping.getUnitsPerPack();
            BigDecimal packagingNeeded = item.units().divide(unitsPerPack, 4, RoundingMode.HALF_UP)
                    .setScale(3, RoundingMode.HALF_UP);

            if (packagingItem.getStockQty().compareTo(packagingNeeded) < 0) {
                results.add(new BatchDtos.BottleItemResult(
                        item.productId(), product.getName(), item.units(),
                        packagingItem.getStockQty(), false,
                        "Insufficient packaging: available " + packagingItem.getStockQty()
                ));
                allSuccess = false;
                continue;
            }

            packagingItem.setStockQty(packagingItem.getStockQty().subtract(packagingNeeded));
            packagingItem.setUpdatedAt(LocalDateTime.now());
            packagingItemRepository.save(packagingItem);

            PackagingStockMovement psm = new PackagingStockMovement();
            psm.setShopId(shopId);
            psm.setItem(packagingItem);
            psm.setChangeQty(packagingNeeded.negate());
            psm.setType(PackagingStockMovement.PackagingMovementType.CONSUME);
            psm.setUnitCost(packagingItem.getCostPerUnit());
            psm.setTotalCost(packagingItem.getCostPerUnit().multiply(packagingNeeded).negate().setScale(2, RoundingMode.HALF_UP));
            psm.setReason("Bottling: " + batch.getCode());
            psm.setReferenceType(BOTTLE_STOCK_REFERENCE);
            psm.setReferenceId(batchId);
            psm.setUser(userService.findById(userId).orElseThrow());
            psm.setCreatedAt(LocalDateTime.now());
            packagingStockMovementRepository.save(psm);

            BatchPackagingUsage usage = new BatchPackagingUsage();
            usage.setBatch(batch);
            usage.setPackagingItem(packagingItem);
            usage.setProduct(product);
            usage.setUnitsUsed(packagingNeeded);
            usage.setCreatedAt(LocalDateTime.now());
            batchPackagingUsageRepository.save(usage);

            inventoryService.addStock(product, item.units(),
                    costingService.bottledUnitCost(shopId, batch, product),
                    batch.getBestBeforeDate(), LocalDateTime.now(), userId);
            recordStockMovement(product, item.units(), StockMovement.MovementType.BATCH_BOTTLE,
                    "Batch bottled: " + batch.getCode(), userId);

            results.add(new BatchDtos.BottleItemResult(
                    item.productId(), product.getName(), item.units(),
                    packagingItem.getStockQty(), true, "Bottled successfully"
            ));
        }

        batch.setStatus(Batch.Status.BOTTLED.name());
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);

        dashboardCache.evict(shopId);
        return new BatchDtos.BottleBatchResponse(batchId, batch.getCode(), results, batch.getStatus(), allSuccess);
    }

    // ==================== GET /batches ====================

    @Transactional(readOnly = true)
    public List<BatchDtos.BatchSummaryResponse> getBatches(Long shopId, BatchDtos.BatchFilter filter) {
        List<Batch> batches;
        if (filter.status() != null && !filter.status().isEmpty()) {
            batches = batchRepository.findByShopIdAndStatus(shopId, filter.status());
        } else {
            batches = batchRepository.findByShopIdOrderByCreatedAtDesc(shopId);
        }
        if (filter.oilTypeId() != null) {
            batches = batches.stream().filter(b -> b.getOilType().getId().equals(filter.oilTypeId())).collect(Collectors.toList());
        }
        if (filter.code() != null && !filter.code().isEmpty()) {
            batches = batches.stream().filter(b -> b.getCode().contains(filter.code())).collect(Collectors.toList());
        }

        LocalDateTime now = LocalDateTime.now();
        return batches.stream()
                .map(b -> {
                    int daysSettling = b.getSettlingStart() != null ?
                            (int) ChronoUnit.DAYS.between(b.getSettlingStart(), now) : 0;
                    return new BatchDtos.BatchSummaryResponse(
                            b.getId(), b.getCode(), b.getOilType().getNameEn(),
                            b.getSeedKg(), b.getOilLitres(), b.getKhaliKg(),
                            b.getYieldPct(), b.getStatus(), daysSettling,
                            b.getPressStart(), b.getCreatedAt().toString()
                    );
                })
                .collect(Collectors.toList());
    }

    // ==================== GET /batches/{id} ====================

    @Transactional(readOnly = true)
    public BatchDtos.BatchDetailResponse getBatchDetail(Long shopId, Long batchId) {
        Batch batch = findBatch(batchId);
        if (!batch.getShopId().equals(shopId)) {
            throw new DomainException(403, "Batch does not belong to this shop");
        }

        List<BatchQualityNote> notes = batchQualityNoteRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        List<BatchDtos.BatchQualityNoteResponse> noteResponses = notes.stream()
                .map(n -> new BatchDtos.BatchQualityNoteResponse(
                        n.getId(), n.getNote(), n.getCheckedBy(), n.getQualityLevel(),
                        n.getCreatedAt().toString()
                )).collect(Collectors.toList());

        int daysSettling = batch.getSettlingStart() != null ?
                (int) ChronoUnit.DAYS.between(batch.getSettlingStart(), LocalDateTime.now()) : 0;

        return new BatchDtos.BatchDetailResponse(
                batch.getId(), batch.getCode(), batch.getOilType().getNameEn(),
                batch.getSeedType().getNameEn(), batch.getSeedKg(),
                batch.getOilLitres(), batch.getKhaliKg(), batch.getYieldPct(),
                batch.getStatus(), batch.getPressStart(), batch.getSettlingStart(),
                batch.getReadyDate(), batch.getMfgDate(), batch.getBestBeforeDate(),
                batch.getNotes(), noteResponses,
                batch.getCreatedAt().toString(), batch.getUpdatedAt().toString()
        );
    }

    // ==================== PUT (PRESSING only) ====================

    @Transactional
    public BatchDtos.BatchDetailResponse updateBatchPressing(Long shopId, Long batchId,
                                                              BatchDtos.CreateBatchRequest request, Long userId) {
        Batch batch = findBatch(batchId);
        verifyStatus(batch, Batch.Status.PRESSING);
        if (request.oilTypeId() != null) {
            batch.setOilType(oilTypeRepository.findById(request.oilTypeId())
                    .orElseThrow(() -> new DomainException(404, "Oil type not found")));
        }
        if (request.seedTypeId() != null) {
            batch.setSeedType(seedTypeRepository.findById(request.seedTypeId())
                    .orElseThrow(() -> new DomainException(404, "Seed type not found")));
        }
        if (request.seedKg() != null) batch.setSeedKg(request.seedKg());
        if (request.pressStart() != null) batch.setPressStart(request.pressStart());
        if (request.notes() != null) batch.setNotes(request.notes());
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        dashboardCache.evict(shopId);
        return toBatchDetailResponse(batch);
    }

    // ==================== DELETE (PRESSING only, restore seed) ====================

    @Transactional
    public void deleteBatch(Long shopId, Long batchId, Long userId) {
        Batch batch = findBatch(batchId);
        verifyStatus(batch, Batch.Status.PRESSING);
        SeedStock seedStock = seedStockRepository.findByShopIdAndSeedTypeId(shopId, batch.getSeedType().getId()).orElse(null);
        if (seedStock != null) {
            seedStock.setAvailableKg(seedStock.getAvailableKg().add(batch.getSeedKg()));
            seedStockRepository.save(seedStock);
            User user = userService.findById(userId).orElseThrow(() -> new DomainException(400, "User not found"));
            recordSeedMovement(shopId, batch.getSeedType(), batch.getSeedKg(),
                    SeedStockMovement.MovementType.ADJUST, seedStock.getAvgRate(),
                    "Batch deleted - seed returned: " + batch.getCode(),
                    null, userId, BATCH_SEED_RETURN_REFERENCE, batchId);
        }
        batchRepository.delete(batch);
        dashboardCache.evict(shopId);
    }

    // ==================== QUALITY NOTES ====================

    @Transactional
    public void addQualityNote(Long shopId, Long batchId, String note, String checkedBy, String qualityLevel) {
        Batch batch = findBatch(batchId);
        if (!batch.getShopId().equals(shopId)) {
            throw new DomainException(403, "Batch does not belong to this shop");
        }
        BatchQualityNote qNote = new BatchQualityNote();
        qNote.setBatch(batch);
        qNote.setNote(note);
        qNote.setCheckedBy(checkedBy);
        qNote.setQualityLevel(qualityLevel);
        qNote.setCreatedAt(LocalDateTime.now());
        batchQualityNoteRepository.save(qNote);
    }

    // ==================== LABEL PDF ====================

    @Transactional(readOnly = true)
    public Map<String, Object> getLabelData(Long shopId, Long batchId) {
        Batch batch = findBatch(batchId);
        if (!batch.getShopId().equals(shopId)) {
            throw new DomainException(403, "Batch does not belong to this shop");
        }
        return Map.of(
                "code", batch.getCode(),
                "oilTypeName", batch.getOilType().getNameEn(),
                "mfgDate", batch.getMfgDate().toString(),
                "bestBeforeDate", batch.getBestBeforeDate().toString(),
                "oilLitres", batch.getOilLitres(),
                "batchId", batch.getId()
        );
    }

    // ==================== HELPERS ====================

    private Batch findBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new DomainException(404, "Batch not found"));
    }

    private void verifyStatus(Batch batch, Batch.Status expectedStatus) {
        if (!batch.getStatus().equals(expectedStatus.name())) {
            throw new DomainException(409, "Batch is in " + batch.getStatus() +
                    " status. Expected: " + expectedStatus.name());
        }
    }

    private String generateBatchCode(Long shopId) {
        LocalDate today = LocalDate.now();
        String datePart = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = batchRepository.countByShopIdAndCreatedAtBetween(
                shopId, today.atStartOfDay(), today.atTime(23, 59, 59)) + 1;
        return BATCH_CODE_PREFIX + datePart + "-" + String.format("%03d", count);
    }

    private Product findOilProductForOilType(Long shopId, Long oilTypeId) {
        return productRepository.findByShopIdAndIsKhaliFalseAndActiveTrueOrderByNameAsc(shopId).stream()
                .filter(p -> p.getOilType() != null && p.getOilType().getId().equals(oilTypeId))
                .findFirst().orElse(null);
    }

    private Product findKhaliProduct(Long shopId, Long oilTypeId) {
        return productRepository.findByShopIdAndIsKhaliTrueAndActiveTrueOrderByNameAsc(shopId).stream()
                .filter(p -> p.getOilType() != null && p.getOilType().getId().equals(oilTypeId))
                .findFirst().orElse(null);
    }

    private void recordSeedMovement(Long shopId, SeedType seedType, BigDecimal qty,
                                      SeedStockMovement.MovementType type, BigDecimal rate,
                                      String reason, String note, Long userId,
                                      String referenceType, Long referenceId) {
        User user = userService.findById(userId).orElseThrow(() -> new DomainException(400, "User not found"));
        SeedStockMovement movement = new SeedStockMovement();
        movement.setShopId(shopId);
        movement.setSeedType(seedType);
        movement.setChangeQty(qty);
        movement.setType(type);
        movement.setRate(rate);
        movement.setTotalValue(rate != null ? rate.multiply(qty.abs()).setScale(2, RoundingMode.HALF_UP) : null);
        movement.setReason(reason);
        movement.setNote(note);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setUser(user);
        movement.setCreatedAt(LocalDateTime.now());
        seedStockMovementRepository.save(movement);
    }

    private void recordStockMovement(Product product, BigDecimal qty,
                                      StockMovement.MovementType type, String reason, Long userId) {
        User user = userService.findById(userId).orElseThrow(() -> new DomainException(400, "User not found"));
        StockMovement movement = new StockMovement();
        movement.setShopId(product.getShopId());
        movement.setProduct(product);
        movement.setBatchId(null);
        movement.setChangeQty(qty);
        movement.setType(type);
        movement.setUnitPrice(product.getCostPrice());
        movement.setTotalValue(product.getCostPrice().multiply(qty.abs()).setScale(2, RoundingMode.HALF_UP));
        movement.setReason(reason);
        movement.setReferenceType(BOTTLE_STOCK_REFERENCE);
        movement.setReferenceId(product.getId());
        movement.setUser(user);
        movement.setCreatedAt(LocalDateTime.now());
        stockMovementRepository.save(movement);
    }

    private BatchDtos.CreateBatchResponse toCreateBatchResponse(Batch batch) {
        return new BatchDtos.CreateBatchResponse(
                batch.getId(), batch.getCode(), batch.getOilType().getNameEn(),
                batch.getSeedType().getNameEn(), batch.getSeedKg(), batch.getOilLitres(),
                batch.getKhaliKg(), batch.getYieldPct(), batch.getStatus(),
                batch.getPressStart(), batch.getCreatedAt()
        );
    }

    private BatchDtos.RecordOutputResponse toRecordOutputResponse(Batch batch) {
        return new BatchDtos.RecordOutputResponse(
                batch.getId(), batch.getCode(), batch.getOilLitres(),
                batch.getKhaliKg(), batch.getYieldPct(), batch.getStatus()
        );
    }

    private BatchDtos.BatchDetailResponse toBatchDetailResponse(Batch batch) {
        return new BatchDtos.BatchDetailResponse(
                batch.getId(), batch.getCode(), batch.getOilType().getNameEn(),
                batch.getSeedType().getNameEn(), batch.getSeedKg(), batch.getOilLitres(),
                batch.getKhaliKg(), batch.getYieldPct(), batch.getStatus(),
                batch.getPressStart(), batch.getSettlingStart(), batch.getReadyDate(),
                batch.getMfgDate(), batch.getBestBeforeDate(), batch.getNotes(),
                List.of(), batch.getCreatedAt().toString(), batch.getUpdatedAt().toString()
        );
    }
}
