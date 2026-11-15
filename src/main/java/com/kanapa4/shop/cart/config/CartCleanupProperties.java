package com.kanapa4.shop.cart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cart.cleanup")
public record CartCleanupProperties(Duration inactiveAfter, int batchSize) {}