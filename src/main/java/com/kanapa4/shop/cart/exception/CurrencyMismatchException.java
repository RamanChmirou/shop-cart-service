package com.kanapa4.shop.cart.exception;

import org.springframework.http.HttpStatus;

public class CurrencyMismatchException extends CartException {

    public CurrencyMismatchException(String cartCurrency, String productCurrency) {
        super(HttpStatus.CONFLICT,
                "Cart currency is " + cartCurrency + ", product currency is " + productCurrency);
    }
}