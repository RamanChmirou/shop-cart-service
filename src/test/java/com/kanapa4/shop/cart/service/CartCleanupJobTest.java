package com.kanapa4.shop.cart.service;

import com.kanapa4.shop.cart.config.CartCleanupProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCleanupJobTest {

    @Mock
    private CartCleanupService cleanupService;

    @Test
    void shouldRepeatWhileBatchesAreFull() {
        CartCleanupJob job = new CartCleanupJob(
                cleanupService, new CartCleanupProperties(Duration.ofDays(3), 100));
        when(cleanupService.deleteInactiveCarts(Duration.ofDays(3), 100)).thenReturn(100, 40);

        job.deleteInactiveCarts();

        verify(cleanupService, times(2)).deleteInactiveCarts(Duration.ofDays(3), 100);
    }
}