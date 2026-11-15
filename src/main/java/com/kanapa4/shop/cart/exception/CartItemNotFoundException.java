package com.kanapa4.shop.cart.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CartItemNotFoundException extends CartException {

    public CartItemNotFoundException(UUID userId, UUID itemId) {
        super(HttpStatus.NOT_FOUND, "Item " + itemId + " not found in the active cart of user " + userId);
    }
}