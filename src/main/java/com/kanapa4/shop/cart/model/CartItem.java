package com.kanapa4.shop.cart.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "currency", nullable = false, length = 3)),
            @AttributeOverride(name = "vatRate",
                    column = @Column(name = "vat_rate", nullable = false, length = 16))
    })
    private Price unitPrice;

    @Column(nullable = false)
    private int quantity;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<SelectedOption> selectedOptions = new ArrayList<>();
}
