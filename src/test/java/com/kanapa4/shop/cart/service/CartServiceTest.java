package com.kanapa4.shop.cart.service;

import com.kanapa4.shop.cart.client.ProductClient;
import com.kanapa4.shop.cart.dto.*;
import com.kanapa4.shop.cart.exception.*;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.kanapa4.shop.cart.testdata.CartFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        assertThat(response.items().getFirst().productName()).isEqualTo(PRODUCT_NAME);
        assertThat(response.items().getFirst().unitPrice()).isEqualTo(laptopPrice());
        assertThat(response.items().getFirst().selectedOptions()).hasSize(2);
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
        assertThat(response.items().getFirst().quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("addItem: merging refreshes the price when the catalog price has changed")
    void addItem_PriceChangedInCatalog_RefreshesUnitPrice() {
        givenActiveCart(cartWith(laptopItem(1)));
        Price newPrice = new Price(new BigDecimal("5399.00"), "PLN", VatRate.VAT_23);
        givenQuote(new PriceQuoteResponse(PRODUCT_ID, PRODUCT_NAME, newPrice, List.of(processorI7(), ram32())));
        givenSaveReturnsArgument();

        CartResponse response = cartService.addItem(USER_ID, addLaptopRequest(1));

        assertThat(response.items().getFirst().unitPrice()).isEqualTo(newPrice);
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

        assertThat(response.items().getFirst().quantity()).isEqualTo(5);
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

    private void givenBatchQuotes(PriceQuoteResponse... quotes) {
        when(productClient.getPriceQuotes(any(BatchPriceQuoteRequest.class)))
                .thenReturn(new BatchPriceQuoteResponse(List.of(quotes)));
    }

    private Price pln(String amount) {
        return new Price(new BigDecimal(amount), "PLN", VatRate.VAT_23);
    }

    private PriceQuoteResponse laptopQuoteWithPrice(Price price) {
        return new PriceQuoteResponse(PRODUCT_ID, PRODUCT_NAME, price, List.of(processorI7(), ram32()));
    }

    private PriceQuoteResponse phoneQuote(Price price) {
        return new PriceQuoteResponse(PHONE_ID, "Phone X", price, List.of());
    }

    @Test
    @DisplayName("reprice: prices unchanged, nothing is reported")
    void reprice_PricesUnchanged_ReturnsNoChanges() {
        givenActiveCart(cartWith(laptopItem(2)));
        givenBatchQuotes(laptopQuote());

        RepriceResponse response = cartService.reprice(USER_ID);

        assertThat(response.changed()).isFalse();
        assertThat(response.changes()).isEmpty();
        assertThat(response.cart().totalAmount()).isEqualByComparingTo("10398.00");
    }

    @Test
    @DisplayName("reprice: a changed catalog price updates the snapshot and is reported")
    void reprice_PriceChanged_UpdatesSnapshotAndReportsChange() {
        Cart cart = cartWith(laptopItem(2));
        givenActiveCart(cart);
        Price newPrice = pln("5399.00");
        givenBatchQuotes(laptopQuoteWithPrice(newPrice));

        RepriceResponse response = cartService.reprice(USER_ID);

        assertThat(response.changed()).isTrue();
        assertThat(response.changes()).hasSize(1);
        assertThat(response.changes().getFirst().itemId()).isEqualTo(ITEM_ID);
        assertThat(response.changes().getFirst().oldUnitPrice()).isEqualTo(laptopPrice());
        assertThat(response.changes().getFirst().newUnitPrice()).isEqualTo(newPrice);
        assertThat(cart.getItems().getFirst().getUnitPrice()).isEqualTo(newPrice);
        assertThat(response.cart().totalAmount()).isEqualByComparingTo("10798.00");
    }

    @Test
    @DisplayName("reprice: the same price with another scale is not a change")
    void reprice_SamePriceDifferentScale_IsNotReportedAsChange() {
        givenActiveCart(cartWith(laptopItem(1)));
        givenBatchQuotes(laptopQuoteWithPrice(pln("5199.0")));

        RepriceResponse response = cartService.reprice(USER_ID);

        assertThat(response.changed()).isFalse();
        assertThat(response.changes()).isEmpty();
    }

    @Test
    @DisplayName("reprice: a renamed product refreshes the name without reporting a price change")
    void reprice_ProductRenamed_UpdatesNameOnly() {
        givenActiveCart(cartWith(laptopItem(1)));
        givenBatchQuotes(new PriceQuoteResponse(PRODUCT_ID, "Laptop Pro 15 (2026)", laptopPrice(),
                List.of(processorI7(), ram32())));

        RepriceResponse response = cartService.reprice(USER_ID);

        assertThat(response.changed()).isFalse();
        assertThat(response.cart().items().getFirst().productName()).isEqualTo("Laptop Pro 15 (2026)");
    }

    @Test
    @DisplayName("reprice: one batch request with all items in cart order")
    void reprice_TwoItems_SendsOneBatchRequestInCartOrder() {
        givenActiveCart(cartWith(laptopItem(1), phoneItem(1)));
        givenBatchQuotes(laptopQuote(), phoneQuote(pln("2499.00")));

        cartService.reprice(USER_ID);

        ArgumentCaptor<BatchPriceQuoteRequest> captor = ArgumentCaptor.forClass(BatchPriceQuoteRequest.class);
        verify(productClient).getPriceQuotes(captor.capture());
        List<BatchPriceQuoteItemRequest> items = captor.getValue().items();
        assertThat(items).extracting(BatchPriceQuoteItemRequest::productId)
                .containsExactly(PRODUCT_ID, PHONE_ID);
        assertThat(items.getFirst().selectedOptions())
                .extracting(SelectedOptionRequest::groupCode, SelectedOptionRequest::valueCode)
                .containsExactly(tuple("PROCESSOR", "I7"), tuple("RAM", "RAM_32"));
    }

    @Test
    @DisplayName("reprice: only the item with a new price is reported")
    void reprice_TwoItemsOneChanged_ReportsOnlyChangedItem() {
        givenActiveCart(cartWith(laptopItem(1), phoneItem(1)));
        givenBatchQuotes(laptopQuote(), phoneQuote(pln("2599.00")));

        RepriceResponse response = cartService.reprice(USER_ID);

        assertThat(response.changed()).isTrue();
        assertThat(response.changes()).hasSize(1);
        assertThat(response.changes().getFirst().itemId()).isEqualTo(PHONE_ITEM_ID);
        assertThat(response.changes().getFirst().productName()).isEqualTo("Phone X");
        assertThat(response.changes().getFirst().oldUnitPrice().amount()).isEqualByComparingTo("2499.00");
        assertThat(response.changes().getFirst().newUnitPrice().amount()).isEqualByComparingTo("2599.00");
        assertThat(response.cart().totalAmount()).isEqualByComparingTo("7798.00");
    }

    @Test
    @DisplayName("reprice: an empty cart does not call product-service")
    void reprice_EmptyCart_DoesNotCallProductService() {
        givenActiveCart(cartWith());

        RepriceResponse response = cartService.reprice(USER_ID);

        assertThat(response.changed()).isFalse();
        assertThat(response.cart().items()).isEmpty();
        verifyNoInteractions(productClient);
    }

    @Test
    @DisplayName("reprice: a user without a cart gets an empty cart and nothing is saved")
    void reprice_NoCart_ReturnsEmptyCartWithoutSaving() {
        givenNoCart();

        RepriceResponse response = cartService.reprice(USER_ID);

        assertThat(response.changed()).isFalse();
        assertThat(response.cart().items()).isEmpty();
        verifyNoInteractions(productClient);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("reprice: a product that no longer exists is propagated and the old price stays")
    void reprice_ProductNotFound_PropagatesAndKeepsOldPrice() {
        Cart cart = cartWith(laptopItem(1));
        givenActiveCart(cart);
        when(productClient.getPriceQuotes(any(BatchPriceQuoteRequest.class)))
                .thenThrow(new ProductNotFoundException("Product not found"));

        assertThatThrownBy(() -> cartService.reprice(USER_ID))
                .isInstanceOf(ProductNotFoundException.class);
        assertThat(cart.getItems().getFirst().getUnitPrice()).isEqualTo(laptopPrice());
    }

    @Test
    @DisplayName("reprice: product-service down is propagated and the old price stays")
    void reprice_ProductServiceUnavailable_PropagatesAndKeepsOldPrice() {
        Cart cart = cartWith(laptopItem(1));
        givenActiveCart(cart);
        when(productClient.getPriceQuotes(any(BatchPriceQuoteRequest.class)))
                .thenThrow(new ServiceUnavailableException("product-service"));

        assertThatThrownBy(() -> cartService.reprice(USER_ID))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(cart.getItems().getFirst().getUnitPrice()).isEqualTo(laptopPrice());
    }

    @Test
    @DisplayName("markCheckedOut: closes the cart of the user")
    void markCheckedOut_ActiveCart_ChangesStatus() {
        Cart cart = cartWith(laptopItem(1));
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));

        cartService.markCheckedOut(cart.getUserId(), cartId);

        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
    }

    @Test
    @DisplayName("markCheckedOut: a repeated event does nothing")
    void markCheckedOut_AlreadyCheckedOut_StaysCheckedOut() {
        Cart cart = cartWith(laptopItem(1));
        cart.checkOut();
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));

        cartService.markCheckedOut(cart.getUserId(), cartId);

        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
    }

    @Test
    @DisplayName("markCheckedOut: an unknown cart is ignored")
    void markCheckedOut_UnknownCart_DoesNothing() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

        cartService.markCheckedOut(USER_ID, cartId);

        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("markCheckedOut: a cart of another user is not touched")
    void markCheckedOut_CartOfAnotherUser_StaysActive() {
        Cart cart = cartWith(laptopItem(1));
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));

        cartService.markCheckedOut(UUID.randomUUID(), cartId);

        assertThat(cart.getStatus()).isEqualTo(CartStatus.ACTIVE);
    }

    @Test
    @DisplayName("addItem: an item added to the cart marks it as recently active")
    void addItem_NewProduct_TouchesCart() {
        Cart cart = cartWith(laptopItem(1));
        cart.setUpdatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        givenActiveCart(cart);
        givenQuote(laptopQuote());
        givenSaveReturnsArgument();

        cartService.addItem(USER_ID, addLaptopRequest(1));

        assertThat(cart.getUpdatedAt()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }
}