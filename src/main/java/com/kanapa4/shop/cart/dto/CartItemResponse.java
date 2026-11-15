package com.kanapa4.shop.cart.dto;

import com.kanapa4.shop.cart.model.Price;
import com.kanapa4.shop.cart.model.SelectedOption;

import java.util.List;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productId,
        String productName,
        Price unitPrice,
        int quantity,
        Price lineTotal,
        List<SelectedOption> selectedOptions) {
}