package com.kanapa4.shop.cart.client;

import com.kanapa4.shop.cart.exception.InvalidProductSelectionException;
import com.kanapa4.shop.cart.exception.ProductNotFoundException;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

public class ProductClientConfig {

    private static final String DEFAULT_NOT_FOUND = "Product not found";
    private static final String DEFAULT_BAD_REQUEST = "Invalid product selection";

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(500, 2000, 3);
    }

    @Bean
    public ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

        return (String methodKey, Response response) -> {
            if (response.status() == 503) {
                return new RetryableException(
                        response.status(),
                        "product-service returned 503 - retrying",
                        response.request().httpMethod(),
                        (Long) null,
                        response.request());
            }
            if (response.status() == 404) {
                return new ProductNotFoundException(readDetail(objectMapper, response, DEFAULT_NOT_FOUND));
            }
            if (response.status() == 400) {
                return new InvalidProductSelectionException(readDetail(objectMapper, response, DEFAULT_BAD_REQUEST));
            }
            return defaultDecoder.decode(methodKey, response);
        };
    }

    private static String readDetail(ObjectMapper objectMapper, Response response, String defaultMessage) {
        if (response.body() == null) {
            return defaultMessage;
        }
        try (InputStream body = response.body().asInputStream()) {
            JsonNode detail = objectMapper.readTree(body).get("detail");
            if (detail == null) {
                return defaultMessage;
            }
            return detail.asString();
        } catch (IOException | JacksonException ex) {
            return defaultMessage;
        }
    }
}