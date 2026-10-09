package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.SeedPurchase;
import com.ghana.dashboard.domain.SeedType;
import com.ghana.dashboard.dto.BeejDtos;
import com.ghana.dashboard.repository.SeedPurchaseRepository;
import com.ghana.dashboard.repository.SeedTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SeedTypeService {

    private final SeedTypeRepository seedTypeRepository;
    private final SeedPurchaseRepository seedPurchaseRepository;

    public SeedTypeService(SeedTypeRepository seedTypeRepository, SeedPurchaseRepository seedPurchaseRepository) {
        this.seedTypeRepository = seedTypeRepository;
        this.seedPurchaseRepository = seedPurchaseRepository;
    }

    @Transactional(readOnly = true)
    public List<BeejDtos.SeedTypeResponse> getAllSeedTypes() {
        return seedTypeRepository.findByActiveTrueOrderByNameEnAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BeejDtos.RateHistoryPoint> getRateHistory(Long shopId, Long seedTypeId) {
        if (!seedTypeRepository.existsById(seedTypeId)) {
            throw new DomainException(404, "Seed type not found");
        }
        return seedPurchaseRepository.findByShopIdAndSeedTypeIdOrderByPurchaseDateAsc(shopId, seedTypeId).stream()
                .map(this::toRateHistoryPoint)
                .collect(Collectors.toList());
    }

    private BeejDtos.SeedTypeResponse toResponse(SeedType seedType) {
        return new BeejDtos.SeedTypeResponse(
                seedType.getId(),
                seedType.getOilType() != null ? seedType.getOilType().getId() : null,
                seedType.getOilType() != null ? seedType.getOilType().getNameEn() : null,
                seedType.getNameEn(),
                seedType.getNameHi(),
                seedType.isActive()
        );
    }

    private BeejDtos.RateHistoryPoint toRateHistoryPoint(SeedPurchase purchase) {
        return new BeejDtos.RateHistoryPoint(
                purchase.getId(),
                purchase.getPurchaseDate(),
                purchase.getRatePerKg(),
                purchase.getQtyKg(),
                purchase.getSupplier().getName()
        );
    }
}
