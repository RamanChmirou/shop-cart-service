package com.kanapa4.shop.cart.model;

import java.math.BigDecimal;

public enum VatRate {
    VAT_23(new BigDecimal("23")),
    VAT_8(new BigDecimal("8")),
    VAT_5(new BigDecimal("5")),
    VAT_0(BigDecimal.ZERO);

    private final BigDecimal percent;

    VatRate(BigDecimal percent) {
        this.percent = percent;
    }

    public BigDecimal percent() {
        return percent;
    }
}