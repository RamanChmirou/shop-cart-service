package com.kanapa4.shop.cart.service;

import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartStatus;
import com.kanapa4.shop.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartCleanupService {

    private final CartRepository cartRepository;
    private final Clock clock;

    @Transactional
    public int deleteInactiveCarts(Duration inactiveAfter, int batchSize) {
        Instant threshold = Instant.now(clock).minus(inactiveAfter);
        List<Cart> carts = cartRepository.findByStatusAndUpdatedAtBefore(
                CartStatus.ACTIVE, threshold, Limit.of(batchSize));
        cartRepository.deleteAll(carts);
        log.info("Deleted {} inactive carts (no activity since {})", carts.size(), threshold);
        return carts.size();
    }
}