package com.kanapa4.shop.cart.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends CartException {

    public ServiceUnavailableException(String serviceName) {
        super(HttpStatus.SERVICE_UNAVAILABLE, serviceName + " is currently unavailable");
    }
}