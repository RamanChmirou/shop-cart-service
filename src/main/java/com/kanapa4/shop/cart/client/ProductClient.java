package com.kanapa4.shop.cart.client;

import com.kanapa4.shop.cart.client.fallback.ProductClientFallbackFactory;
import com.kanapa4.shop.cart.dto.BatchPriceQuoteRequest;
import com.kanapa4.shop.cart.dto.BatchPriceQuoteResponse;
import com.kanapa4.shop.cart.dto.PriceQuoteRequest;
import com.kanapa4.shop.cart.dto.PriceQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
        name = "product-service",
        url = "${clients.product-service.url}",
        configuration = ProductClientConfig.class,
        fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @PostMapping("/products/{productId}/price-quote")
    PriceQuoteResponse getPriceQuote(@PathVariable("productId") UUID productId,
                                     @RequestBody PriceQuoteRequest request);

    @PostMapping("/products/price-quotes")
    BatchPriceQuoteResponse getPriceQuotes(@RequestBody BatchPriceQuoteRequest request);
}