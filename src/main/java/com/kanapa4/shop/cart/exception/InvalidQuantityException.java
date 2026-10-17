package com.kanapa4.shop.cart.exception;

import org.springframework.http.HttpStatus;

public class InvalidQuantityException extends CartException {

    public InvalidQuantityException(int maxQuantity) {
        super(HttpStatus.BAD_REQUEST, "Quantity of a single item cannot exceed " + maxQuantity);
    }
}