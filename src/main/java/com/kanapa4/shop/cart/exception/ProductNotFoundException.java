package com.kanapa4.shop.cart.exception;

import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends CartException {

    public ProductNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}