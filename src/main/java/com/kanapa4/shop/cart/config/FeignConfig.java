package com.kanapa4.shop.cart.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.kanapa4.shop.cart.client")
public class FeignConfig {
}