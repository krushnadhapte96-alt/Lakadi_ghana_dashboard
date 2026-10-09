package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.SeedStock;
import com.ghana.dashboard.domain.SeedStockMovement;
import com.ghana.dashboard.domain.SeedType;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.BeejDtos;
import com.ghana.dashboard.repository.SeedStockMovementRepository;
import com.ghana.dashboard.repository.SeedStockRepository;
import com.ghana.dashboard.repository.SeedTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SeedStockService {

    static final BigDecimal DEFAULT_LOW_THRESHOLD = new BigDecimal("100.000");
    private static final int TREND_POINTS = 10;

    private final SeedStockRepository seedStockRepository;
    private final SeedStockMovementRepository movementRepository;
    private final SeedTypeRepository seedTypeRepository;
    private final UserService userService;

    public SeedStockService(SeedStockRepository seedStockRepository,
                            SeedStockMovementRepository movementRepository,
                            SeedTypeRepository seedTypeRepository,
                            UserService userService) {
        this.seedStockRepository = seedStockRepository;
        this.movementRepository = movementRepository;
        this.seedTypeRepository = seedTypeRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public BigDecimal getAvailable(Long shopId, Long seedTypeId) {
        return seedStockRepository.findByShopIdAndSeedTypeId(shopId, seedTypeId)
                .map(SeedStock::getAvailableKg)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<BeejDtos.SeedStockResponse> getStockCards(Long shopId) {
        return seedTypeRepository.findByActiveTrueOrderByNameEnAsc().stream()
                .map(seedType -> toStockResponse(shopId, seedType))
                .collect(Collectors.toList());
    }

    @Transactional
    public void add(Long shopId, SeedType seedType, BigDecimal qty, BigDecimal rate, Long userId,
                    String referenceType, Long referenceId) {
        SeedStock stock = getOrCreate(shopId, seedType);
        BigDecimal oldQty = stock.getAvailableKg();
        BigDecimal newQty = oldQty.add(qty);
        BigDecimal newAvg = newQty.signum() == 0
                ? BigDecimal.ZERO
                : oldQty.multiply(stock.getAvgRate()).add(qty.multiply(rate))
                        .divide(newQty, 2, RoundingMode.HALF_UP);

        stock.setAvailableKg(newQty);
        stock.setAvgRate(newAvg);
        seedStockRepository.save(stock);

        recordMovement(shopId, seedType, qty, SeedStockMovement.MovementType.PURCHASE_IN, rate, null, null,
                userId, referenceType, referenceId);
    }

    @Transactional
    public void consume(Long shopId, Long seedTypeId, BigDecimal qty, String reason, Long userId,
                        String referenceType, Long referenceId) {
        SeedStock stock = seedStockRepository.findByShopIdAndSeedTypeId(shopId, seedTypeId)
                .orElseThrow(() -> new DomainException(400, "No seed stock available for this seed type"));

        if (stock.getAvailableKg().compareTo(qty) < 0) {
            throw new DomainException(400, "Insufficient seed stock. Available: " + stock.getAvailableKg()
                    + " kg, requested: " + qty + " kg");
        }

        stock.setAvailableKg(stock.getAvailableKg().subtract(qty));
        seedStockRepository.save(stock);

        recordMovement(shopId, stock.getSeedType(), qty.negate(), SeedStockMovement.MovementType.BATCH_OUT,
                stock.getAvgRate(), reason, null, userId, referenceType, referenceId);
    }

    @Transactional
    public void adjust(Long shopId, Long seedTypeId, BeejDtos.SeedStockAdjustRequest request, Long userId) {
        SeedStock stock = seedStockRepository.findByShopIdAndSeedTypeId(shopId, seedTypeId)
                .orElseThrow(() -> new DomainException(404, "Seed stock not found for this seed type"));

        if (request.type() == SeedStockMovement.MovementType.WASTE && request.qty().signum() > 0) {
            throw new DomainException(400, "Waste quantity must be negative");
        }
        if (request.type() != SeedStockMovement.MovementType.ADJUST
                && request.type() != SeedStockMovement.MovementType.WASTE) {
            throw new DomainException(400, "Only ADJUST or WASTE movements are allowed here");
        }

        BigDecimal qty = request.qty();
        BigDecimal newQty = stock.getAvailableKg().add(qty);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(400, "Adjustment would result in negative stock");
        }

        stock.setAvailableKg(newQty);
        seedStockRepository.save(stock);

        recordMovement(shopId, stock.getSeedType(), qty, request.type(), stock.getAvgRate(),
                request.reason(), request.note(), userId, null, null);
    }

    /** Only reversible when nothing else has moved this seed type's stock since the purchase was recorded. */
    @Transactional
    public void reversePurchase(Long shopId, Long seedTypeId, BigDecimal qty, BigDecimal rate, Long purchaseId) {
        SeedStock stock = seedStockRepository.findByShopIdAndSeedTypeId(shopId, seedTypeId)
                .orElseThrow(() -> new DomainException(404, "Seed stock not found for this seed type"));

        SeedStockMovement latest = movementRepository.findTopByShopIdAndSeedTypeIdOrderByCreatedAtDesc(shopId, seedTypeId);
        boolean isLatest = latest != null
                && "SEED_PURCHASE".equals(latest.getReferenceType())
                && purchaseId.equals(latest.getReferenceId());
        if (!isLatest) {
            throw new DomainException(409, "Cannot delete this purchase: stock has moved since it was recorded");
        }

        BigDecimal newQty = stock.getAvailableKg().subtract(qty);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(409, "Cannot delete this purchase: stock has moved since it was recorded");
        }

        BigDecimal newAvg = newQty.signum() == 0
                ? BigDecimal.ZERO
                : stock.getAvailableKg().multiply(stock.getAvgRate()).subtract(qty.multiply(rate))
                        .divide(newQty, 2, RoundingMode.HALF_UP);

        stock.setAvailableKg(newQty);
        stock.setAvgRate(newAvg);
        seedStockRepository.save(stock);

        movementRepository.delete(latest);
    }

    private SeedStock getOrCreate(Long shopId, SeedType seedType) {
        return seedStockRepository.findByShopIdAndSeedTypeId(shopId, seedType.getId())
                .orElseGet(() -> {
                    SeedStock stock = new SeedStock();
                    stock.setShopId(shopId);
                    stock.setSeedType(seedType);
                    stock.setAvailableKg(BigDecimal.ZERO);
                    stock.setAvgRate(BigDecimal.ZERO);
                    stock.setLowStockThreshold(DEFAULT_LOW_THRESHOLD);
                    return seedStockRepository.save(stock);
                });
    }

    private void recordMovement(Long shopId, SeedType seedType, BigDecimal qty, SeedStockMovement.MovementType type,
                                BigDecimal rate, String reason, String note, Long userId,
                                String referenceType, Long referenceId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new DomainException(400, "User not found"));

        BigDecimal totalValue = rate != null ? rate.multiply(qty.abs()).setScale(2, RoundingMode.HALF_UP) : null;

        SeedStockMovement movement = new SeedStockMovement();
        movement.setShopId(shopId);
        movement.setSeedType(seedType);
        movement.setChangeQty(qty);
        movement.setType(type);
        movement.setRate(rate);
        movement.setTotalValue(totalValue);
        movement.setReason(reason);
        movement.setNote(note);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setUser(user);
        movement.setCreatedAt(LocalDateTime.now());

        movementRepository.save(movement);
    }

    private BeejDtos.SeedStockResponse toStockResponse(Long shopId, SeedType seedType) {
        SeedStock stock = seedStockRepository.findByShopIdAndSeedTypeId(shopId, seedType.getId()).orElse(null);
        BigDecimal available = stock != null ? stock.getAvailableKg() : BigDecimal.ZERO;
        BigDecimal avgRate = stock != null ? stock.getAvgRate() : BigDecimal.ZERO;
        BigDecimal threshold = stock != null ? stock.getLowStockThreshold() : DEFAULT_LOW_THRESHOLD;

        return new BeejDtos.SeedStockResponse(
                seedType.getId(),
                seedType.getNameEn(),
                available,
                avgRate,
                threshold,
                available.compareTo(threshold) < 0,
                buildStockTrend(shopId, seedType.getId(), available)
        );
    }

    private List<BigDecimal> buildStockTrend(Long shopId, Long seedTypeId, BigDecimal currentAvailable) {
        List<SeedStockMovement> recent = movementRepository.findByShopIdAndSeedTypeIdOrderByCreatedAtDesc(shopId, seedTypeId)
                .stream()
                .limit(TREND_POINTS)
                .collect(Collectors.toList());
        Collections.reverse(recent);

        if (recent.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal sumOfWindow = recent.stream().map(SeedStockMovement::getChangeQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal runningBalance = currentAvailable.subtract(sumOfWindow);

        List<BigDecimal> trend = new ArrayList<>();
        for (SeedStockMovement movement : recent) {
            runningBalance = runningBalance.add(movement.getChangeQty());
            trend.add(runningBalance);
        }
        return trend;
    }
}
