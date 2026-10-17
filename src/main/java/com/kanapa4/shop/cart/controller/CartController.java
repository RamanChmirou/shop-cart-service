package com.kanapa4.shop.cart.controller;

import com.kanapa4.shop.cart.dto.AddCartItemRequest;
import com.kanapa4.shop.cart.dto.CartResponse;
import com.kanapa4.shop.cart.dto.UpdateQuantityRequest;
import com.kanapa4.shop.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/carts/{userId}")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Active shopping cart of a user")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get the active cart",
            description = "Returns the active cart of the user. If the user has no cart yet, an empty cart is returned.")
    @ApiResponse(responseCode = "200", description = "Cart returned")
    public CartResponse getCart(@Parameter(description = "Keycloak user id") @PathVariable UUID userId) {
        log.info("GET /carts/{}", userId);
        return cartService.getCart(userId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a product to the cart",
            description = "The price is taken from product-service. The same product with the same options "
                    + "increases the quantity of the existing item.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product added, updated cart returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request or invalid product options",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Currency mismatch or concurrent modification",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "product-service is unavailable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public CartResponse addItem(@PathVariable UUID userId,
                                @Valid @RequestBody AddCartItemRequest request) {
        log.info("POST /carts/{}/items productId={} quantity={}", userId, request.productId(), request.quantity());
        return cartService.addItem(userId, request);
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Change the quantity of a cart item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quantity changed, updated cart returned"),
            @ApiResponse(responseCode = "400", description = "Invalid quantity",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Item not found in the active cart",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public CartResponse updateQuantity(@PathVariable UUID userId,
                                       @PathVariable UUID itemId,
                                       @Valid @RequestBody UpdateQuantityRequest request) {
        log.info("PATCH /carts/{}/items/{} quantity={}", userId, itemId, request.quantity());
        return cartService.updateQuantity(userId, itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an item from the cart")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Item removed"),
            @ApiResponse(responseCode = "404", description = "Item not found in the active cart",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void removeItem(@PathVariable UUID userId, @PathVariable UUID itemId) {
        log.info("DELETE /carts/{}/items/{}", userId, itemId);
        cartService.removeItem(userId, itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear the cart", description = "Removes all items. Does nothing if the cart does not exist.")
    @ApiResponse(responseCode = "204", description = "Cart cleared")
    public void clear(@PathVariable UUID userId) {
        log.info("DELETE /carts/{}", userId);
        cartService.clear(userId);
    }
}