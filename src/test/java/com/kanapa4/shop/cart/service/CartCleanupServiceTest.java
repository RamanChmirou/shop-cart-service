package com.kanapa4.shop.cart.service;

import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartStatus;
import com.kanapa4.shop.cart.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCleanupServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CartRepository cartRepository;

    @Test
    void shouldDeleteActiveCartsInactiveLongerThanThreshold() {
        CartCleanupService service = new CartCleanupService(cartRepository, CLOCK);
        Cart stale = new Cart();
        Instant threshold = Instant.parse("2026-10-27T12:00:00Z");
        when(cartRepository.findByStatusAndUpdatedAtBefore(eq(CartStatus.ACTIVE), eq(threshold), any(Limit.class)))
                .thenReturn(List.of(stale));

        int deleted = service.deleteInactiveCarts(Duration.ofDays(3), 100);

        assertThat(deleted).isEqualTo(1);
        verify(cartRepository).deleteAll(List.of(stale));
    }

    @Test
    void shouldDeleteNothing_whenNoStaleCarts() {
        CartCleanupService service = new CartCleanupService(cartRepository, CLOCK);
        when(cartRepository.findByStatusAndUpdatedAtBefore(any(), any(), any(Limit.class)))
                .thenReturn(List.of());

        assertThat(service.deleteInactiveCarts(Duration.ofDays(3), 100)).isZero();
    }
}