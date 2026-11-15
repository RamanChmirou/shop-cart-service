package com.kanapa4.shop.cart.mapper;

import com.kanapa4.shop.cart.dto.*;
import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartItem;
import com.kanapa4.shop.cart.model.Price;
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

    BatchPriceQuoteItemRequest toQuoteItem(CartItem item);

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "productId", source = "item.productId")
    @Mapping(target = "productName", source = "item.productName")
    @Mapping(target = "oldUnitPrice", source = "oldPrice")
    @Mapping(target = "newUnitPrice", source = "item.unitPrice")
    PriceChange toPriceChange(CartItem item, Price oldPrice);
}