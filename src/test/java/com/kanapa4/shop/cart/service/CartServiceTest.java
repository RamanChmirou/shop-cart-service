package com.kanapa4.shop.cart.service;

import com.kanapa4.shop.cart.client.ProductClient;
import com.kanapa4.shop.cart.dto.CartResponse;
import com.kanapa4.shop.cart.dto.PriceQuoteRequest;
import com.kanapa4.shop.cart.dto.PriceQuoteResponse;
import com.kanapa4.shop.cart.exception.CartItemNotFoundException;
import com.kanapa4.shop.cart.exception.CurrencyMismatchException;
import com.kanapa4.shop.cart.exception.InvalidQuantityException;
import com.kanapa4.shop.cart.exception.ProductNotFoundException;
import com.kanapa4.shop.cart.mapper.CartMapper;
import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartStatus;
import com.kanapa4.shop.cart.model.Price;
import com.kanapa4.shop.cart.model.VatRate;
import com.kanapa4.shop.cart.repository.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.kanapa4.shop.cart.testdata.CartFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductClient productClient;

    @Spy
    private CartMapper cartMapper = Mappers.getMapper(CartMapper.class);

    @InjectMocks
    private CartService cartService;

    private void givenActiveCart(Cart cart) {
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
    }

    private void givenNoCart() {
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE)).thenReturn(Optional.empty());
    }

    private void givenQuote(PriceQuoteResponse quote) {
        when(productClient.getPriceQuote(eq(PRODUCT_ID), any(PriceQuoteRequest.class))).thenReturn(quote);
    }

    private void givenSaveReturnsArgument() {
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("getCart: user without a cart gets an empty cart")
    void getCart_NoCart_ReturnsEmptyCart() {
        givenNoCart();

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.items()).isEmpty();
        assertThat(response.itemCount()).isZero();
        assertThat(response.totalAmount()).isEqualByComparingTo("0");
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCart: returns items and the total of an existing cart")
    void getCart_ExistingCart_ReturnsItemsAndTotal() {
        givenActiveCart(cartWith(laptopItem(2)));

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.items()).hasSize(1);
        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("10398.00");
        assertThat(response.currency()).isEqualTo("PLN");
    }

    @Test
    @DisplayName("addItem: first product creates the cart with a price snapshot from product-service")
    void addItem_NewProduct_CreatesCartWithSnapshot() {
        givenNoCart();
        givenQuote(laptopQuote());
        givenSaveReturnsArgument();

        CartResponse response = cartService.addItem(USER_ID, addLaptopRequest(2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo(PRODUCT_NAME);
        assertThat(response.items().get(0).unitPrice()).isEqualTo(laptopPrice());
        assertThat(response.items().get(0).selectedOptions()).hasSize(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("10398.00");
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    @DisplayName("addItem: the same product with the same options increases the quantity")
    void addItem_SameProductAndOptions_MergesQuantity() {
        givenActiveCart(cartWith(laptopItem(1)));
        givenQuote(laptopQuote());
        givenSaveReturnsArgument();

        CartResponse response = cartService.addItem(USER_ID, addLaptopRequest(2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("addItem: merging refreshes the price when the catalog price has changed")
    void addItem_PriceChangedInCatalog_RefreshesUnitPrice() {
        givenActiveCart(cartWith(laptopItem(1)));
        Price newPrice = new Price(new BigDecimal("5399.00"), "PLN", VatRate.VAT_23);
        givenQuote(new PriceQuoteResponse(PRODUCT_ID, PRODUCT_NAME, newPrice, List.of(processorI7(), ram32())));
        givenSaveReturnsArgument();

        CartResponse response = cartService.addItem(USER_ID, addLaptopRequest(1));

        assertThat(response.items().get(0).unitPrice()).isEqualTo(newPrice);
        assertThat(response.totalAmount()).isEqualByComparingTo("10798.00");
    }

    @Test
    @DisplayName("addItem: the same product with different options is a separate item")
    void addItem_SameProductDifferentOptions_AddsSecondItem() {
        givenActiveCart(cartWith(laptopItem(1)));
        givenQuote(new PriceQuoteResponse(PRODUCT_ID, PRODUCT_NAME, laptopPrice(), List.of(processorI7())));
        givenSaveReturnsArgument();

        CartResponse response = cartService.addItem(USER_ID, addLaptopRequest(1));

        assertThat(response.items()).hasSize(2);
    }

    @Test
    @DisplayName("addItem: merged quantity above the limit is rejected")
    void addItem_MergeExceedsLimit_ThrowsInvalidQuantity() {
        givenActiveCart(cartWith(laptopItem(99)));
        givenQuote(laptopQuote());

        assertThatThrownBy(() -> cartService.addItem(USER_ID, addLaptopRequest(2)))
                .isInstanceOf(InvalidQuantityException.class);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem: a product in another currency than the cart is rejected")
    void addItem_CurrencyMismatch_ThrowsCurrencyMismatch() {
        givenActiveCart(cartWith(laptopItem(1)));
        Price euroPrice = new Price(new BigDecimal("1200.00"), "EUR", VatRate.VAT_23);
        givenQuote(new PriceQuoteResponse(PRODUCT_ID, PRODUCT_NAME, euroPrice, List.of(processorI7(), ram32())));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, addLaptopRequest(1)))
                .isInstanceOf(CurrencyMismatchException.class);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem: product-service errors are propagated and nothing is saved")
    void addItem_ProductNotFound_PropagatesAndSavesNothing() {
        when(productClient.getPriceQuote(eq(PRODUCT_ID), any(PriceQuoteRequest.class)))
                .thenThrow(new ProductNotFoundException("Product not found"));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, addLaptopRequest(1)))
                .isInstanceOf(ProductNotFoundException.class);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateQuantity: changes the quantity of an existing item")
    void updateQuantity_ExistingItem_ChangesQuantity() {
        givenActiveCart(cartWith(laptopItem(1)));

        CartResponse response = cartService.updateQuantity(USER_ID, ITEM_ID, 5);

        assertThat(response.items().get(0).quantity()).isEqualTo(5);
        assertThat(response.totalAmount()).isEqualByComparingTo("25995.00");
    }

    @Test
    @DisplayName("updateQuantity: unknown item")
    void updateQuantity_UnknownItem_ThrowsNotFound() {
        givenActiveCart(cartWith(laptopItem(1)));

        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, UUID.randomUUID(), 2))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("updateQuantity: user without an active cart")
    void updateQuantity_NoCart_ThrowsNotFound() {
        givenNoCart();

        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, ITEM_ID, 2))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("removeItem: removes an existing item")
    void removeItem_ExistingItem_RemovesIt() {
        Cart cart = cartWith(laptopItem(1));
        givenActiveCart(cart);

        cartService.removeItem(USER_ID, ITEM_ID);

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("removeItem: unknown item")
    void removeItem_UnknownItem_ThrowsNotFound() {
        givenActiveCart(cartWith(laptopItem(1)));

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, UUID.randomUUID()))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("clear: removes all items of the active cart")
    void clear_ExistingCart_RemovesAllItems() {
        Cart cart = cartWith(laptopItem(1));
        givenActiveCart(cart);

        cartService.clear(USER_ID);

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("clear: user without a cart, nothing happens")
    void clear_NoCart_DoesNothing() {
        givenNoCart();

        cartService.clear(USER_ID);

        verify(cartRepository, never()).save(any());
    }
}