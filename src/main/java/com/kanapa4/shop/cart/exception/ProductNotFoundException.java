package com.kanapa4.shop.cart.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ProductNotFoundException extends CartException {

    public ProductNotFoundException(UUID productId) {
        super(HttpStatus.NOT_FOUND, "Product " + productId + " not found");
    }
}