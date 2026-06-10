package com.estefano.deliverybackbone.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id")
    private Long categoryId;

    private String sku;

    private String name;

    private BigDecimal price;

    private boolean active = true;

    @Column(name = "stock_total")
    private int stockTotal;

    @Column(name = "stock_reserved")
    private int stockReserved;

    /** Optimistic locking para ediciones desde el panel admin (defensa secundaria). */
    @Version
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Stock disponible SIEMPRE derivado, nunca almacenado (regla del proyecto). */
    public int getStockAvailable() {
        return stockTotal - stockReserved;
    }

    public Product(Long categoryId, String sku, String name, BigDecimal price, int initialStock) {
        this.categoryId = categoryId;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.stockTotal = initialStock;
    }
}
