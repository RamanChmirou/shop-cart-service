package com.kanapa4.shop.cart.dto;

import jakarta.validation.constraints.NotBlank;

public record SelectedOptionRequest(
        @NotBlank String groupCode,
        @NotBlank String valueCode) {
}