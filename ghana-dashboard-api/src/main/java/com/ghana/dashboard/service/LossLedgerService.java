package com.ghana.dashboard.service;

import com.ghana.dashboard.domain.LossLedger;
import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.ExpenseDtos;
import com.ghana.dashboard.repository.LossLedgerRepository;
import com.ghana.dashboard.repository.ProductRepository;
import com.ghana.dashboard.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LossLedgerService {

    private final LossLedgerRepository lossLedgerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public LossLedgerService(LossLedgerRepository lossLedgerRepository,
                             ProductRepository productRepository,
                             UserRepository userRepository) {
        this.lossLedgerRepository = lossLedgerRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ExpenseDtos.LossLedgerResponse> getLossEntries(Long shopId, LocalDate from, LocalDate to,
                                                               LossLedger.LossType type, Long productId) {
        List<LossLedger> losses = lossLedgerRepository.search(shopId, from, to, type, productId);

        Set<Long> productIds = losses.stream().map(LossLedger::getProductId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = losses.stream().map(LossLedger::getRecordedBy).collect(Collectors.toSet());
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return losses.stream().map(l -> {
            Product product = l.getProductId() != null ? products.get(l.getProductId()) : null;
            User user = users.get(l.getRecordedBy());
            return new ExpenseDtos.LossLedgerResponse(
                    l.getId(), l.getLossType(), l.getProductId(),
                    product != null ? product.getName() : null,
                    l.getBatchId(), l.getQty(), l.getValue(), l.getReferenceNo(), l.getDescription(),
                    l.getLossDate(), user != null ? user.getName() : "", String.valueOf(l.getCreatedAt()));
        }).toList();
    }

    @Transactional
    public LossLedger recordLoss(Long shopId, LossLedger.LossType lossType, Long productId, String batchId,
                                  BigDecimal qty, BigDecimal value, String description,
                                  LocalDate lossDate, Long recordedBy) {
        LossLedger loss = new LossLedger();
        loss.setShopId(shopId);
        loss.setLossType(lossType);
        loss.setProductId(productId);
        loss.setBatchId(batchId);
        loss.setQty(qty);
        loss.setValue(value);
        loss.setDescription(description);
        loss.setLossDate(lossDate);
        loss.setRecordedBy(recordedBy);
        return lossLedgerRepository.save(loss);
    }
}
