package com.kanapa4.shop.cart.dto;

import com.kanapa4.shop.cart.model.Price;

import java.util.UUID;

public record PriceChange(
        UUID itemId,
        UUID productId,
        String productName,
        Price oldUnitPrice,
        Price newUnitPrice) {
}