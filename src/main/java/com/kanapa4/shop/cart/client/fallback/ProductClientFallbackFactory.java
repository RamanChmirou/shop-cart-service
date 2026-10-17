package com.kanapa4.shop.cart.client.fallback;

import com.kanapa4.shop.cart.client.ProductClient;
import com.kanapa4.shop.cart.dto.PriceQuoteRequest;
import com.kanapa4.shop.cart.dto.PriceQuoteResponse;
import com.kanapa4.shop.cart.exception.CartException;
import com.kanapa4.shop.cart.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    private static final String SERVICE_NAME = "product-service";

    @Override
    public ProductClient create(Throwable cause) {
        return (productId, request) -> {
            Optional<CartException> cartException = CartException.findIn(cause);
            if (cartException.isPresent()) {
                throw cartException.get();
            }
            log.error("Fallback for {} (product {}): {}", SERVICE_NAME, productId, cause.toString());
            throw new ServiceUnavailableException(SERVICE_NAME);
        };
    }
}