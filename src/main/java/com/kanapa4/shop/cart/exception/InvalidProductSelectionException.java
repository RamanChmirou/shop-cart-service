package com.kanapa4.shop.cart.exception;

import org.springframework.http.HttpStatus;

public class InvalidProductSelectionException extends CartException {

    public InvalidProductSelectionException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}