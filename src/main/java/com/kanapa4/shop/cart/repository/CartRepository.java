package com.kanapa4.shop.cart.repository;

import com.kanapa4.shop.cart.model.Cart;
import com.kanapa4.shop.cart.model.CartStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);

    List<Cart> findByStatusAndUpdatedAtBefore(CartStatus status, Instant threshold, Limit limit);
}