package com.kanapa4.shop.cart.util;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class TestFileUtils {

    private TestFileUtils() {
    }

    public static String readJson(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read test JSON file: " + path, e);
        }
    }
}

