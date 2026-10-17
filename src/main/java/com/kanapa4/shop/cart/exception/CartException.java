package com.kanapa4.shop.cart.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class CartException extends RuntimeException {

    private final HttpStatus status;

    protected CartException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}