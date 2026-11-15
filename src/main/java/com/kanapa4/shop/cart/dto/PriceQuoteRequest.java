package com.kanapa4.shop.cart.dto;

import java.util.List;

public record PriceQuoteRequest(List<SelectedOptionRequest> selectedOptions) {
}