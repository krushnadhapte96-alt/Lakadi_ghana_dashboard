package com.ghana.dashboard.service;

import com.ghana.dashboard.domain.Shop;
import com.ghana.dashboard.dto.ExpiryAlertDtos;
import com.ghana.dashboard.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily sweep for expiring stock. It just finds and logs what's due; the actual push/SMS/email
 * notification is wired up in a later prompt, this only needs to produce the same buckets
 * {@link ExpiryAlertsService#getAlerts} would show so that hook has something to fire on.
 */
@Component
public class ExpiryAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryAlertScheduler.class);

    private final ShopRepository shopRepository;
    private final ExpiryAlertsService expiryAlertsService;

    public ExpiryAlertScheduler(ShopRepository shopRepository, ExpiryAlertsService expiryAlertsService) {
        this.shopRepository = shopRepository;
        this.expiryAlertsService = expiryAlertsService;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Kolkata")
    public void sweep() {
        LocalDate today = LocalDate.now();
        for (Shop shop : shopRepository.findAll()) {
            List<ExpiryAlertDtos.ExpiryAlertItemResponse> items = expiryAlertsService.alertableItems(shop.getId(), today);
            if (items.isEmpty()) {
                continue;
            }
            Map<String, Long> countByBucket = items.stream()
                    .collect(Collectors.groupingBy(ExpiryAlertDtos.ExpiryAlertItemResponse::bucket, Collectors.counting()));
            log.info("Expiry sweep shop={} lots={} byBucket={}", shop.getId(), items.size(), countByBucket);
        }
    }
}
