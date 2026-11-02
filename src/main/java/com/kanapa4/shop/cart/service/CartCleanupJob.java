package com.kanapa4.shop.cart.service;

import com.kanapa4.shop.cart.config.CartCleanupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartCleanupJob {

    private final CartCleanupService cleanupService;
    private final CartCleanupProperties properties;

    @Scheduled(cron = "${cart.cleanup.cron}")
    public void deleteInactiveCarts() {
        int total = 0;
        int deleted;
        do {
            deleted = cleanupService.deleteInactiveCarts(properties.inactiveAfter(), properties.batchSize());
            total += deleted;
        } while (deleted == properties.batchSize());
        log.info("Cart cleanup finished, {} carts deleted", total);
    }
}