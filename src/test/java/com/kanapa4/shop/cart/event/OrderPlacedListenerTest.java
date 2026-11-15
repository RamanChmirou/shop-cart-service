package com.kanapa4.shop.cart.event;

import com.kanapa4.shop.cart.service.CartService;
import com.kanapa4.shop.cart.util.TestFileUtils;
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
        String payloadTemplate = TestFileUtils.readJson("events/order-placed-template.json");
        String payload = payloadTemplate.formatted(UUID.randomUUID(), UUID.randomUUID(), userId, cartId);

        listener.onOrderPlaced(payload);

        verify(cartService).markCheckedOut(userId, cartId);
    }
}