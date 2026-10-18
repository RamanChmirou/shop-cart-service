package com.kanapa4.shop.cart.controller;

import com.kanapa4.shop.cart.dto.*;
import com.kanapa4.shop.cart.exception.CartItemNotFoundException;
import com.kanapa4.shop.cart.exception.CurrencyMismatchException;
import com.kanapa4.shop.cart.exception.InvalidProductSelectionException;
import com.kanapa4.shop.cart.exception.ProductNotFoundException;
import com.kanapa4.shop.cart.exception.ServiceUnavailableException;
import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartStatus;
import com.kanapa4.shop.cart.model.Price;
import com.kanapa4.shop.cart.model.VatRate;
import com.kanapa4.shop.cart.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static com.kanapa4.shop.cart.testdata.CartFixtures.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
class CartControllerTest {

    private static final String CART_URL = "/carts/{userId}";
    private static final String ITEMS_URL = "/carts/{userId}/items";
    private static final String ITEM_URL = "/carts/{userId}/items/{itemId}";
    private static final String REPRICE_URL = "/carts/{userId}/reprice";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    private CartResponse cartResponse() {
        CartItemResponse item = new CartItemResponse(ITEM_ID, PRODUCT_ID, PRODUCT_NAME,
                laptopPrice(), 2, laptopPrice().times(2), List.of(processorI7(), ram32()));
        return new CartResponse(CART_ID, USER_ID, CartStatus.ACTIVE, List.of(item), 2,
                new BigDecimal("10398.00"), "PLN");
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("GET cart: 200 with items and totals")
    void getCart_ExistingCart_Return200() throws Exception {
        when(cartService.getCart(USER_ID)).thenReturn(cartResponse());

        mockMvc.perform(get(CART_URL, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].productName").value(PRODUCT_NAME))
                .andExpect(jsonPath("$.items[0].unitPrice.currency").value("PLN"))
                .andExpect(jsonPath("$.items[0].selectedOptions.length()").value(2))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.totalAmount").value(10398.0))
                .andExpect(jsonPath("$.currency").value("PLN"));
    }

    @Test
    @DisplayName("GET cart: user id that is not a UUID gives 400")
    void getCart_InvalidUserId_Return400() throws Exception {
        mockMvc.perform(get("/carts/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("POST item: 201 and the request is passed to the service")
    void addItem_ValidRequest_Return201() throws Exception {
        when(cartService.addItem(eq(USER_ID), any(AddCartItemRequest.class))).thenReturn(cartResponse());

        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(addLaptopRequest(2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        verify(cartService).addItem(USER_ID, addLaptopRequest(2));
    }

    @Test
    @DisplayName("POST item: quantity 0 gives 400")
    void addItem_QuantityZero_Return400() throws Exception {
        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddCartItemRequest(PRODUCT_ID, 0, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("quantity")));

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("POST item: quantity above 100 gives 400")
    void addItem_QuantityAboveLimit_Return400() throws Exception {
        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddCartItemRequest(PRODUCT_ID, 101, List.of()))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("POST item: missing productId gives 400")
    void addItem_MissingProductId_Return400() throws Exception {
        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("productId")));

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("POST item: blank option code gives 400")
    void addItem_BlankOptionCode_Return400() throws Exception {
        String body = """
                { "productId": "11111111-0000-0000-0000-000000000001", "quantity": 1,
                  "selectedOptions": [ { "groupCode": "", "valueCode": "I7" } ] }
                """;

        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("POST item: malformed JSON gives 400")
    void addItem_MalformedJson_Return400() throws Exception {
        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("POST item: product not found gives 404")
    void addItem_ProductNotFound_Return404() throws Exception {
        when(cartService.addItem(eq(USER_ID), any(AddCartItemRequest.class)))
                .thenThrow(new ProductNotFoundException("Product not found: " + PRODUCT_ID));

        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(addLaptopRequest(1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found: " + PRODUCT_ID));
    }

    @Test
    @DisplayName("POST item: invalid options give 400")
    void addItem_InvalidOptions_Return400() throws Exception {
        when(cartService.addItem(eq(USER_ID), any(AddCartItemRequest.class)))
                .thenThrow(new InvalidProductSelectionException("Unknown option value NOPE"));

        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(addLaptopRequest(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown option value NOPE"));
    }

    @Test
    @DisplayName("POST item: currency mismatch gives 409")
    void addItem_CurrencyMismatch_Return409() throws Exception {
        when(cartService.addItem(eq(USER_ID), any(AddCartItemRequest.class)))
                .thenThrow(new CurrencyMismatchException("PLN", "EUR"));

        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(addLaptopRequest(1))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST item: concurrent modification gives 409")
    void addItem_ConcurrentModification_Return409() throws Exception {
        when(cartService.addItem(eq(USER_ID), any(AddCartItemRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Cart.class, CART_ID));

        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(addLaptopRequest(1))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST item: product-service unavailable gives 503")
    void addItem_ProductServiceUnavailable_Return503() throws Exception {
        when(cartService.addItem(eq(USER_ID), any(AddCartItemRequest.class)))
                .thenThrow(new ServiceUnavailableException("product-service"));

        mockMvc.perform(post(ITEMS_URL, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(addLaptopRequest(1))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("product-service is currently unavailable"));
    }

    @Test
    @DisplayName("PATCH item: 200 with the updated cart")
    void updateQuantity_ValidRequest_Return200() throws Exception {
        when(cartService.updateQuantity(USER_ID, ITEM_ID, 5)).thenReturn(cartResponse());

        mockMvc.perform(patch(ITEM_URL, USER_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateQuantityRequest(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ITEM_ID.toString()));

        verify(cartService).updateQuantity(USER_ID, ITEM_ID, 5);
    }

    @Test
    @DisplayName("PATCH item: quantity 0 gives 400")
    void updateQuantity_QuantityZero_Return400() throws Exception {
        mockMvc.perform(patch(ITEM_URL, USER_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateQuantityRequest(0))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("PATCH item: unknown item gives 404")
    void updateQuantity_UnknownItem_Return404() throws Exception {
        when(cartService.updateQuantity(USER_ID, ITEM_ID, 2))
                .thenThrow(new CartItemNotFoundException(USER_ID, ITEM_ID));

        mockMvc.perform(patch(ITEM_URL, USER_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateQuantityRequest(2))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE item: 204")
    void removeItem_ExistingItem_Return204() throws Exception {
        mockMvc.perform(delete(ITEM_URL, USER_ID, ITEM_ID))
                .andExpect(status().isNoContent());

        verify(cartService).removeItem(USER_ID, ITEM_ID);
    }

    @Test
    @DisplayName("DELETE item: unknown item gives 404")
    void removeItem_UnknownItem_Return404() throws Exception {
        doThrow(new CartItemNotFoundException(USER_ID, ITEM_ID))
                .when(cartService).removeItem(USER_ID, ITEM_ID);

        mockMvc.perform(delete(ITEM_URL, USER_ID, ITEM_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE cart: 204")
    void clear_Return204() throws Exception {
        mockMvc.perform(delete(CART_URL, USER_ID))
                .andExpect(status().isNoContent());

        verify(cartService).clear(USER_ID);
    }

    @Test
    @DisplayName("POST reprice: 200 with the cart and the changed prices")
    void reprice_PricesChanged_Return200WithChanges() throws Exception {
        Price newPrice = new Price(new BigDecimal("5399.00"), "PLN", VatRate.VAT_23);
        PriceChange change = new PriceChange(ITEM_ID, PRODUCT_ID, PRODUCT_NAME, laptopPrice(), newPrice);
        when(cartService.reprice(USER_ID)).thenReturn(new RepriceResponse(cartResponse(), true, List.of(change)));

        mockMvc.perform(post(REPRICE_URL, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].itemId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.changes[0].oldUnitPrice.amount").value(5199.0))
                .andExpect(jsonPath("$.changes[0].newUnitPrice.amount").value(5399.0))
                .andExpect(jsonPath("$.cart.items[0].productName").value(PRODUCT_NAME));
    }

    @Test
    @DisplayName("POST reprice: 200 and no changes")
    void reprice_NoChanges_Return200() throws Exception {
        when(cartService.reprice(USER_ID)).thenReturn(new RepriceResponse(cartResponse(), false, List.of()));

        mockMvc.perform(post(REPRICE_URL, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.changes").isEmpty());
    }

    @Test
    @DisplayName("POST reprice: a product that no longer exists gives 404")
    void reprice_ProductNotFound_Return404() throws Exception {
        when(cartService.reprice(USER_ID))
                .thenThrow(new ProductNotFoundException("Product not found: " + PRODUCT_ID));

        mockMvc.perform(post(REPRICE_URL, USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found: " + PRODUCT_ID));
    }

    @Test
    @DisplayName("POST reprice: product-service unavailable gives 503")
    void reprice_ProductServiceUnavailable_Return503() throws Exception {
        when(cartService.reprice(USER_ID)).thenThrow(new ServiceUnavailableException("product-service"));

        mockMvc.perform(post(REPRICE_URL, USER_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("product-service is currently unavailable"));
    }

    @Test
    @DisplayName("POST reprice: user id that is not a UUID gives 400")
    void reprice_InvalidUserId_Return400() throws Exception {
        mockMvc.perform(post("/carts/not-a-uuid/reprice"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }
}