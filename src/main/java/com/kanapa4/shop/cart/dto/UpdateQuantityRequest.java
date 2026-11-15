package com.kanapa4.shop.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateQuantityRequest(
        @Min(1) @Max(100) int quantity) {
}