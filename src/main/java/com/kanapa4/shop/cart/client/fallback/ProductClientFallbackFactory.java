package com.kanapa4.shop.cart.client.fallback;

import com.kanapa4.shop.cart.client.ProductClient;
import com.kanapa4.shop.cart.dto.BatchPriceQuoteRequest;
import com.kanapa4.shop.cart.dto.BatchPriceQuoteResponse;
import com.kanapa4.shop.cart.dto.PriceQuoteRequest;
import com.kanapa4.shop.cart.dto.PriceQuoteResponse;
import com.kanapa4.shop.cart.exception.CartException;
import com.kanapa4.shop.cart.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    private static final String SERVICE_NAME = "product-service";

    @Override
    public ProductClient create(Throwable cause) {
        return new ProductClient() {
            @Override
            public PriceQuoteResponse getPriceQuote(UUID productId, PriceQuoteRequest request) {
                log.error("Fallback for {} (product {}): {}", SERVICE_NAME, productId, cause.toString());
                throw failure(cause);
            }

            @Override
            public BatchPriceQuoteResponse getPriceQuotes(BatchPriceQuoteRequest request) {
                log.error("Fallback for {} (batch of {} items): {}",
                        SERVICE_NAME, request.items().size(), cause.toString());
                throw failure(cause);
            }
        };
    }

    private CartException failure(Throwable cause) {
        return CartException.findIn(cause)
                .orElseGet(() -> new ServiceUnavailableException(SERVICE_NAME));
    }
}