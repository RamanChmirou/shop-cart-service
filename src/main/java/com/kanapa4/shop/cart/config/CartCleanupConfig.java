package com.kanapa4.shop.cart.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CartCleanupProperties.class)
public class CartCleanupConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}