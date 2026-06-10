package com.estefano.deliverybackbone.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductResponse(
            Long id,
            Long categoryId,
            String sku,
            String name,
            BigDecimal price,
            int stockAvailable,
            boolean active) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getCategoryId(), p.getSku(), p.getName(),
                    p.getPrice(), p.getStockAvailable(), p.isActive());
        }
    }

    public record CreateProductRequest(
            @NotNull Long categoryId,
            @NotBlank String sku,
            @NotBlank String name,
            @NotNull @PositiveOrZero BigDecimal price,
            @PositiveOrZero int initialStock) {
    }

    public record RestockRequest(@Positive int quantity) {
    }
}
