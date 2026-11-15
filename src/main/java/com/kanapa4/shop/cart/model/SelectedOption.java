package com.kanapa4.shop.cart.model;

import java.math.BigDecimal;

public record SelectedOption(
        String groupCode,
        String valueCode,
        String valueLabel,
        BigDecimal priceDelta) {
}
