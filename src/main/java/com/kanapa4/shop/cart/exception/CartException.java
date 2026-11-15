package com.kanapa4.shop.cart.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Getter
public abstract class CartException extends RuntimeException {

    private final HttpStatus status;

    protected CartException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static Optional<CartException> findIn(Throwable throwable) {
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause)
                .filter(CartException.class::isInstance)
                .map(CartException.class::cast)
                .findFirst();
    }
}