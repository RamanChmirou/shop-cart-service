package com.kanapa4.shop.cart.dto;

import com.kanapa4.shop.cart.model.Price;
import com.kanapa4.shop.cart.model.SelectedOption;

import java.util.List;
import java.util.UUID;

public record PriceQuoteResponse(
        UUID productId,
        String productName,
        Price unitPrice,
        List<SelectedOption> selectedOptions) {
}