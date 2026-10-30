package com.kanapa4.shop.cart.event;

import com.kanapa4.shop.cart.service.CartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPlacedListenerTest {

    @Mock
    private CartService cartService;

    @Test
    void shouldCheckOutCartFromMessage() {
        OrderPlacedListener listener = new OrderPlacedListener(cartService, JsonMapper.builder().build());
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","orderId":"%s","orderNumber":"ORD-1","userId":"%s","cartId":"%s",
                 "grandTotal":215.00,"currency":"PLN","occurredAt":"2026-10-18T12:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), userId, cartId);

        listener.onOrderPlaced(payload);

        verify(cartService).markCheckedOut(userId, cartId);
    }
}