package com.kanapa4.shop.cart.dto;

import com.kanapa4.shop.cart.model.CartStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        UUID userId,
        CartStatus status,
        List<CartItemResponse> items,
        int itemCount,
        BigDecimal totalAmount,
        String currency) {
}