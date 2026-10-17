package com.kanapa4.shop.cart.service;

import com.kanapa4.shop.cart.client.ProductClient;
import com.kanapa4.shop.cart.dto.AddCartItemRequest;
import com.kanapa4.shop.cart.dto.CartResponse;
import com.kanapa4.shop.cart.dto.PriceQuoteRequest;
import com.kanapa4.shop.cart.dto.PriceQuoteResponse;
import com.kanapa4.shop.cart.exception.CartItemNotFoundException;
import com.kanapa4.shop.cart.exception.CurrencyMismatchException;
import com.kanapa4.shop.cart.exception.InvalidQuantityException;
import com.kanapa4.shop.cart.mapper.CartMapper;
import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartItem;
import com.kanapa4.shop.cart.model.CartStatus;
import com.kanapa4.shop.cart.model.Price;
import com.kanapa4.shop.cart.model.SelectedOption;
import com.kanapa4.shop.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    public static final int MAX_ITEM_QUANTITY = 100;

    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final CartMapper cartMapper;

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        log.debug("Getting cart of user {}", userId);
        return cartMapper.toResponse(getActiveCartOrNew(userId));
    }

    @Transactional
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        log.info("Adding product {} (x{}) to cart of user {}", request.productId(), request.quantity(), userId);
        PriceQuoteResponse quote = requestQuote(request);
        Cart cart = getActiveCartOrNew(userId);
        checkCurrency(cart, quote.unitPrice());
        addOrMerge(cart, request, quote);
        return cartMapper.toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateQuantity(UUID userId, UUID itemId, int quantity) {
        log.info("Setting quantity of item {} to {} in cart of user {}", itemId, quantity, userId);
        CartItem item = findItemOrThrow(getActiveCartOrThrow(userId, itemId), userId, itemId);
        item.setQuantity(quantity);
        return cartMapper.toResponse(item.getCart());
    }

    @Transactional
    public void removeItem(UUID userId, UUID itemId) {
        log.info("Removing item {} from cart of user {}", itemId, userId);
        Cart cart = getActiveCartOrThrow(userId, itemId);
        cart.getItems().remove(findItemOrThrow(cart, userId, itemId));
    }

    @Transactional
    public void clear(UUID userId) {
        log.info("Clearing cart of user {}", userId);
        cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .ifPresent(cart -> cart.getItems().clear());
    }

    private PriceQuoteResponse requestQuote(AddCartItemRequest request) {
        return productClient.getPriceQuote(
                request.productId(), new PriceQuoteRequest(request.selectedOptions()));
    }

    private Cart getActiveCartOrNew(UUID userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> newCart(userId));
    }

    private Cart newCart(UUID userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        return cart;
    }

    private Cart getActiveCartOrThrow(UUID userId, UUID itemId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new CartItemNotFoundException(userId, itemId));
    }

    private CartItem findItemOrThrow(Cart cart, UUID userId, UUID itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException(userId, itemId));
    }

    private void checkCurrency(Cart cart, Price newPrice) {
        cart.getItems().stream()
                .findFirst()
                .filter(item -> !item.getUnitPrice().currency().equals(newPrice.currency()))
                .ifPresent(item -> {
                    throw new CurrencyMismatchException(item.getUnitPrice().currency(), newPrice.currency());
                });
    }

    private void addOrMerge(Cart cart, AddCartItemRequest request, PriceQuoteResponse quote) {
        Optional<CartItem> existing = findSameItem(cart, request.productId(), quote.selectedOptions());
        if (existing.isPresent()) {
            mergeIntoExisting(existing.get(), quote, request.quantity());
        } else {
            cart.addItem(cartMapper.toItem(request, quote));
        }
    }

    private Optional<CartItem> findSameItem(Cart cart, UUID productId, List<SelectedOption> options) {
        Set<String> wantedKeys = optionKeys(options);
        return cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .filter(item -> optionKeys(item.getSelectedOptions()).equals(wantedKeys))
                .findFirst();
    }

    private Set<String> optionKeys(List<SelectedOption> options) {
        return options.stream()
                .map(option -> option.groupCode() + ":" + option.valueCode())
                .collect(Collectors.toSet());
    }

    private void mergeIntoExisting(CartItem item, PriceQuoteResponse quote, int addedQuantity) {
        int newQuantity = item.getQuantity() + addedQuantity;
        if (newQuantity > MAX_ITEM_QUANTITY) {
            throw new InvalidQuantityException(MAX_ITEM_QUANTITY);
        }
        item.setQuantity(newQuantity);
        item.setUnitPrice(quote.unitPrice());
    }
}