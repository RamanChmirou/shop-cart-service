package com.kanapa4.shop.cart.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedMessage(UUID eventId, UUID orderId, UUID userId, UUID cartId) {}