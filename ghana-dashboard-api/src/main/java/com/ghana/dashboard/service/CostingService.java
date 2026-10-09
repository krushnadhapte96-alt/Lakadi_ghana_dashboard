package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.Batch;
import com.ghana.dashboard.domain.BatchCost;
import com.ghana.dashboard.domain.CostDefaults;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.ProductPackaging;
import com.ghana.dashboard.dto.CostingDtos;
import com.ghana.dashboard.repository.BatchCostRepository;
import com.ghana.dashboard.repository.BatchRepository;
import com.ghana.dashboard.repository.CostDefaultsRepository;
import com.ghana.dashboard.repository.ProductPackagingRepository;
import com.ghana.dashboard.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Batch costing: seed cost is locked in at batch creation (weighted-avg rate x seed kg);
 * electricity/labour/other overheads and the khali credit are recomputed from cost_defaults
 * each time the batch is costed, so a defaults change only affects batches costed after it.
 */
@Service
public class CostingService {

    private final CostDefaultsRepository costDefaultsRepository;
    private final BatchCostRepository batchCostRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final ProductPackagingRepository productPackagingRepository;

    public CostingService(CostDefaultsRepository costDefaultsRepository,
                          BatchCostRepository batchCostRepository,
                          BatchRepository batchRepository,
                          ProductRepository productRepository,
                          ProductPackagingRepository productPackagingRepository) {
        this.costDefaultsRepository = costDefaultsRepository;
        this.batchCostRepository = batchCostRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.productPackagingRepository = productPackagingRepository;
    }

    // ==================== COST DEFAULTS ====================

    @Transactional
    public CostingDtos.CostDefaultsResponse getCostDefaults(Long shopId) {
        return toCostDefaultsResponse(getOrCreateDefaults(shopId));
    }

    @Transactional
    public CostingDtos.CostDefaultsResponse updateCostDefaults(Long shopId, CostingDtos.CostDefaultsRequest request) {
        CostDefaults defaults = getOrCreateDefaults(shopId);
        defaults.setElectricityMode(parseMode(request.electricityMode()));
        defaults.setElectricityValue(request.electricityValue());
        defaults.setLabourMode(parseMode(request.labourMode()));
        defaults.setLabourValue(request.labourValue());
        defaults.setOtherMode(parseMode(request.otherMode()));
        defaults.setOtherValue(request.otherValue());
        defaults.setKhaliValuePerKg(request.khaliValuePerKg());
        defaults.setTargetMarginPct(request.targetMarginPct());
        costDefaultsRepository.save(defaults);
        return toCostDefaultsResponse(defaults);
    }

    private CostDefaults getOrCreateDefaults(Long shopId) {
        return costDefaultsRepository.findByShopId(shopId).orElseGet(() -> {
            CostDefaults defaults = new CostDefaults();
            defaults.setShopId(shopId);
            return costDefaultsRepository.save(defaults);
        });
    }

    private CostDefaults.OverheadMode parseMode(String mode) {
        try {
            return CostDefaults.OverheadMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            throw new DomainException(400, "Mode must be one of PER_BATCH, PER_KG");
        }
    }

    // ==================== BATCH COST ====================

    @Transactional
    public CostingDtos.BatchCostResponse getBatchCost(Long shopId, Long batchId) {
        Batch batch = findBatch(shopId, batchId);
        BatchCost cost = batchCostRepository.findByBatchId(batchId)
                .orElseGet(() -> computeAndSave(shopId, batch));
        return toBatchCostResponse(batch, cost);
    }

    /** Auto-recompute, called from BatchService after output is recorded or the batch is bottled. */
    @Transactional
    public void autoComputeBatchCost(Long shopId, Long batchId) {
        Batch batch = findBatch(shopId, batchId);
        computeAndSave(shopId, batch);
    }

    @Transactional
    public CostingDtos.BatchCostResponse recalculateBatchCost(Long shopId, Long batchId) {
        Batch batch = findBatch(shopId, batchId);
        BatchCost cost = computeAndSave(shopId, batch);
        return toBatchCostResponse(batch, cost);
    }

    @Transactional
    public CostingDtos.BatchCostResponse updateBatchCost(Long shopId, Long batchId,
                                                          CostingDtos.BatchCostUpdateRequest request) {
        Batch batch = findBatch(shopId, batchId);
        BatchCost cost = batchCostRepository.findByBatchId(batchId).orElseGet(() -> computeAndSave(shopId, batch));

        if (request.electricityCost() != null) cost.setElectricityCost(request.electricityCost());
        if (request.labourCost() != null) cost.setLabourCost(request.labourCost());
        if (request.otherCost() != null) cost.setOtherCost(request.otherCost());
        if (request.khaliCredit() != null) cost.setKhaliCredit(request.khaliCredit());
        cost.setManualOverride(true);
        cost.setComputedAt(LocalDateTime.now());
        applyTotals(cost, batch);
        batchCostRepository.save(cost);
        return toBatchCostResponse(batch, cost);
    }

    private BatchCost computeAndSave(Long shopId, Batch batch) {
        CostDefaults defaults = getOrCreateDefaults(shopId);
        BatchCost cost = batchCostRepository.findByBatchId(batch.getId()).orElseGet(() -> {
            BatchCost c = new BatchCost();
            c.setBatch(batch);
            c.setShopId(shopId);
            return c;
        });

        cost.setSeedCost(batch.getSeedCost());
        cost.setElectricityCost(overhead(defaults.getElectricityMode(), defaults.getElectricityValue(), batch.getSeedKg()));
        cost.setLabourCost(overhead(defaults.getLabourMode(), defaults.getLabourValue(), batch.getSeedKg()));
        cost.setOtherCost(overhead(defaults.getOtherMode(), defaults.getOtherValue(), batch.getSeedKg()));
        cost.setKhaliCredit(batch.getKhaliKg().multiply(defaults.getKhaliValuePerKg()).setScale(2, RoundingMode.HALF_UP));
        cost.setManualOverride(false);
        cost.setComputedAt(LocalDateTime.now());
        applyTotals(cost, batch);
        return batchCostRepository.save(cost);
    }

    private void applyTotals(BatchCost cost, Batch batch) {
        BigDecimal total = cost.getSeedCost()
                .add(cost.getElectricityCost())
                .add(cost.getLabourCost())
                .add(cost.getOtherCost())
                .subtract(cost.getKhaliCredit())
                .setScale(2, RoundingMode.HALF_UP);
        cost.setTotalCost(total);
        cost.setCostPerLitre(batch.getOilLitres().signum() > 0
                ? total.divide(batch.getOilLitres(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
    }

    private BigDecimal overhead(CostDefaults.OverheadMode mode, BigDecimal value, BigDecimal seedKg) {
        return mode == CostDefaults.OverheadMode.PER_KG
                ? value.multiply(seedKg).setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Batch findBatch(Long shopId, Long batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new DomainException(404, "Batch not found"));
        if (!batch.getShopId().equals(shopId)) {
            throw new DomainException(403, "Batch does not belong to this shop");
        }
        return batch;
    }

    private Optional<BatchCost> latestBatchCost(Long shopId, Product product) {
        if (product.isKhali() || product.getOilType() == null) {
            return Optional.empty();
        }
        return batchCostRepository
                .findByShopIdAndBatch_OilType_IdOrderByComputedAtDesc(shopId, product.getOilType().getId())
                .stream().findFirst();
    }

    // ==================== COSTING PRODUCTS ====================

    @Transactional
    public List<CostingDtos.CostingProductResponse> getCostingProducts(Long shopId) {
        CostDefaults defaults = getOrCreateDefaults(shopId);
        return productRepository.findByShopIdAndActiveTrueOrderByNameAsc(shopId).stream()
                .map(product -> toCostingProductResponse(shopId, product, defaults))
                .toList();
    }

    @Transactional
    public CostingDtos.ApplySuggestedPriceResponse applySuggestedPrice(Long shopId, Long productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getShopId().equals(shopId))
                .orElseThrow(() -> new DomainException(404, "Product not found"));
        CostDefaults defaults = getOrCreateDefaults(shopId);
        CostingDtos.CostingProductResponse costing = toCostingProductResponse(shopId, product, defaults);

        product.setSellingPrice(costing.suggestedPrice());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        return new CostingDtos.ApplySuggestedPriceResponse(product.getId(), product.getSellingPrice());
    }

    private CostingDtos.CostingProductResponse toCostingProductResponse(Long shopId, Product product, CostDefaults defaults) {
        Optional<BatchCost> sourceBatchCost = latestBatchCost(shopId, product);
        BigDecimal baseCost = product.isKhali()
                ? defaults.getKhaliValuePerKg()
                : sourceBatchCost.map(BatchCost::getCostPerLitre).orElse(null);

        BigDecimal unitCost;
        if (baseCost != null) {
            unitCost = baseCost.multiply(packQuantity(product))
                    .add(packagingCostPerUnit(shopId, product))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            unitCost = product.getCostPrice();
        }

        BigDecimal sellingPrice = product.getSellingPrice();
        BigDecimal marginPct = sellingPrice.signum() > 0
                ? sellingPrice.subtract(unitCost).multiply(BigDecimal.valueOf(100))
                        .divide(sellingPrice, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal targetMarginPct = defaults.getTargetMarginPct();
        BigDecimal marginFraction = BigDecimal.ONE.subtract(
                targetMarginPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal suggestedPrice = marginFraction.signum() > 0
                ? unitCost.divide(marginFraction, 2, RoundingMode.HALF_UP)
                : unitCost;

        Batch sourceBatch = sourceBatchCost.map(BatchCost::getBatch).orElse(null);
        return new CostingDtos.CostingProductResponse(
                product.getId(), product.getName(), product.getSku(), product.isKhali(),
                unitCost, sellingPrice, marginPct, targetMarginPct, suggestedPrice,
                marginPct.compareTo(targetMarginPct) < 0,
                sourceBatch != null ? sourceBatch.getId() : null,
                sourceBatch != null ? sourceBatch.getCode() : null
        );
    }

    /** Product pack size expressed in the unit batch costs are priced in (litres for oil, kg for khali). */
    private BigDecimal packQuantity(Product product) {
        BigDecimal packSize = product.getPackSize();
        if (product.isKhali()) {
            return packSize;
        }
        return switch (product.getUnit()) {
            case ML -> packSize.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            case L, KG -> packSize;
        };
    }

    private BigDecimal packagingCostPerUnit(Long shopId, Product product) {
        List<ProductPackaging> mappings = productPackagingRepository.findByShopIdAndProductId(shopId, product.getId());
        BigDecimal total = BigDecimal.ZERO;
        for (ProductPackaging mapping : mappings) {
            if (!mapping.isActive() || mapping.getUnitsPerPack().signum() == 0) {
                continue;
            }
            total = total.add(mapping.getPackagingItem().getCostPerUnit()
                    .divide(mapping.getUnitsPerPack(), 4, RoundingMode.HALF_UP));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** The per-unit cost to charge inventory when a product is bottled from this batch. */
    @Transactional
    public BigDecimal bottledUnitCost(Long shopId, Batch batch, Product product) {
        BatchCost cost = batchCostRepository.findByBatchId(batch.getId()).orElse(null);
        BigDecimal baseCost = product.isKhali()
                ? getOrCreateDefaults(shopId).getKhaliValuePerKg()
                : (cost != null ? cost.getCostPerLitre() : null);
        if (baseCost == null) {
            return product.getCostPrice();
        }
        return baseCost.multiply(packQuantity(product))
                .add(packagingCostPerUnit(shopId, product))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private CostingDtos.CostDefaultsResponse toCostDefaultsResponse(CostDefaults d) {
        return new CostingDtos.CostDefaultsResponse(
                d.getElectricityMode().name(), d.getElectricityValue(),
                d.getLabourMode().name(), d.getLabourValue(),
                d.getOtherMode().name(), d.getOtherValue(),
                d.getKhaliValuePerKg(), d.getTargetMarginPct(), d.getUpdatedAt()
        );
    }

    private CostingDtos.BatchCostResponse toBatchCostResponse(Batch batch, BatchCost cost) {
        return new CostingDtos.BatchCostResponse(
                batch.getId(), batch.getCode(), batch.getSeedKg(), batch.getOilLitres(), batch.getKhaliKg(),
                cost.getSeedCost(), cost.getElectricityCost(), cost.getLabourCost(), cost.getOtherCost(),
                cost.getKhaliCredit(), cost.getTotalCost(), cost.getCostPerLitre(),
                cost.isManualOverride(), cost.getComputedAt()
        );
    }
}
