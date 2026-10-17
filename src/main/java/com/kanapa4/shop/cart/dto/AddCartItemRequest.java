package com.kanapa4.shop.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AddCartItemRequest(
        @NotNull UUID productId,
        @Min(1) @Max(100) int quantity,
        @Valid List<SelectedOptionRequest> selectedOptions) {

    public AddCartItemRequest {
        if (selectedOptions == null) {
            selectedOptions = List.of();
        }
    }
}