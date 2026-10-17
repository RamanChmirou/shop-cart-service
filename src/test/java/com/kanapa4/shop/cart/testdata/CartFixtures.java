package com.kanapa4.shop.cart.testdata;

import com.kanapa4.shop.cart.dto.AddCartItemRequest;
import com.kanapa4.shop.cart.dto.PriceQuoteResponse;
import com.kanapa4.shop.cart.dto.SelectedOptionRequest;
import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartItem;
import com.kanapa4.shop.cart.model.Price;
import com.kanapa4.shop.cart.model.SelectedOption;
import com.kanapa4.shop.cart.model.VatRate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CartFixtures {

    public static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    public static final UUID CART_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    public static final UUID ITEM_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    public static final UUID PRODUCT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    public static final String PRODUCT_NAME = "Laptop Pro 15";

    private CartFixtures() {
    }

    public static SelectedOption processorI7() {
        return new SelectedOption("PROCESSOR", "I7", "Intel Core i7", new BigDecimal("800.00"));
    }

    public static SelectedOption ram32() {
        return new SelectedOption("RAM", "RAM_32", "32 GB", new BigDecimal("400.00"));
    }

    public static Price laptopPrice() {
        return new Price(new BigDecimal("5199.00"), "PLN", VatRate.VAT_23);
    }

    public static AddCartItemRequest addLaptopRequest(int quantity) {
        return new AddCartItemRequest(PRODUCT_ID, quantity, List.of(
                new SelectedOptionRequest("PROCESSOR", "I7"),
                new SelectedOptionRequest("RAM", "RAM_32")));
    }

    public static PriceQuoteResponse laptopQuote() {
        return new PriceQuoteResponse(PRODUCT_ID, PRODUCT_NAME, laptopPrice(),
                List.of(processorI7(), ram32()));
    }

    public static CartItem laptopItem(int quantity) {
        CartItem item = new CartItem();
        item.setId(ITEM_ID);
        item.setProductId(PRODUCT_ID);
        item.setProductName(PRODUCT_NAME);
        item.setUnitPrice(laptopPrice());
        item.setQuantity(quantity);
        item.setSelectedOptions(new ArrayList<>(List.of(processorI7(), ram32())));
        return item;
    }

    public static Cart cartWith(CartItem... items) {
        Cart cart = new Cart();
        cart.setId(CART_ID);
        cart.setUserId(USER_ID);
        for (CartItem item : items) {
            cart.addItem(item);
        }
        return cart;
    }
}