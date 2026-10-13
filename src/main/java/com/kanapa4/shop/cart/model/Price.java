package com.kanapa4.shop.cart.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Embeddable
public record Price(
        @Column(name = "amount", nullable = false, precision = 19, scale = 2)
        BigDecimal amount,                      // cena brutto

        @Column(name = "currency", nullable = false, length = 3)
        String currency,

        @Enumerated(EnumType.STRING)
        @Column(name = "vat_rate", nullable = false, length = 16)
        VatRate vatRate) {

    public Price {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(vatRate, "vatRate");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal netAmount() {
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(100).add(vatRate.percent()), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal taxAmount() {
        return amount.subtract(netAmount());
    }

    public Price plus(Price other) {
        requireSame(other);
        return new Price(amount.add(other.amount), currency, vatRate);
    }

    public Price times(int quantity) {
        return new Price(amount.multiply(BigDecimal.valueOf(quantity)), currency, vatRate);
    }

    private void requireSame(Price other) {
        if (!currency.equals(other.currency) || vatRate != other.vatRate) {
            throw new IllegalArgumentException("Currency or VAT rate mismatch");
        }
    }
}