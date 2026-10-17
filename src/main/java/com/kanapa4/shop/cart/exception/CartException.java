package com.kanapa4.shop.cart.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Optional;

@Getter
public abstract class CartException extends RuntimeException {

    private final HttpStatus status;

    protected CartException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static Optional<CartException> findIn(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CartException cartException) {
                return Optional.of(cartException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}