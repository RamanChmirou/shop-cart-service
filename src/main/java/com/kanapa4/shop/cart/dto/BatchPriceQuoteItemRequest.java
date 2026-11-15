package com.kanapa4.shop.cart.dto;

import java.util.List;
import java.util.UUID;

public record BatchPriceQuoteItemRequest(UUID productId, List<SelectedOptionRequest> selectedOptions) {
}