package com.kanapa4.shop.cart.mapper;

import com.kanapa4.shop.cart.dto.AddCartItemRequest;
import com.kanapa4.shop.cart.dto.CartItemResponse;
import com.kanapa4.shop.cart.dto.CartResponse;
import com.kanapa4.shop.cart.dto.PriceQuoteResponse;
import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface CartMapper {

    String DEFAULT_CURRENCY = "PLN";

    @Mapping(target = "itemCount", expression = "java(countItems(cart))")
    @Mapping(target = "totalAmount", expression = "java(calculateTotal(cart))")
    @Mapping(target = "currency", expression = "java(resolveCurrency(cart))")
    CartResponse toResponse(Cart cart);

    @Mapping(target = "lineTotal", expression = "java(item.getUnitPrice().times(item.getQuantity()))")
    CartItemResponse toItemResponse(CartItem item);

    default int countItems(Cart cart) {
        return cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    default BigDecimal calculateTotal(Cart cart) {
        return cart.getItems().stream()
                .map(item -> item.getUnitPrice().times(item.getQuantity()).amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    default String resolveCurrency(Cart cart) {
        return cart.getItems().stream()
                .findFirst()
                .map(item -> item.getUnitPrice().currency())
                .orElse(DEFAULT_CURRENCY);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cart", ignore = true)
    @Mapping(target = "productId", source = "request.productId")
    @Mapping(target = "quantity", source = "request.quantity")
    @Mapping(target = "productName", source = "quote.productName")
    @Mapping(target = "unitPrice", source = "quote.unitPrice")
    @Mapping(target = "selectedOptions", source = "quote.selectedOptions")
    CartItem toItem(AddCartItemRequest request, PriceQuoteResponse quote);
}