package com.kanapa4.shop.cart.dto;

import java.util.List;

public record RepriceResponse(CartResponse cart, boolean changed, List<PriceChange> changes) {
}