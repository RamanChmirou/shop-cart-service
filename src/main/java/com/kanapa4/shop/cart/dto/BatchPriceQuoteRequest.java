package com.kanapa4.shop.cart.dto;

import java.util.List;

public record BatchPriceQuoteRequest(List<BatchPriceQuoteItemRequest> items) {
}