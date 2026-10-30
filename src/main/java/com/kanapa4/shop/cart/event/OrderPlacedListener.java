package com.kanapa4.shop.cart.event;

import com.kanapa4.shop.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedListener {

    private final CartService cartService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${cart.kafka.order-placed-topic}")
    public void onOrderPlaced(String payload) {
        OrderPlacedMessage message = objectMapper.readValue(payload, OrderPlacedMessage.class);
        log.info("Received order.placed for order {} (cart {}, user {})",
                message.orderId(), message.cartId(), message.userId());
        cartService.markCheckedOut(message.userId(), message.cartId());
    }
}